package net.epconsortium.cryptomarket;

import net.epconsortium.cryptomarket.commands.CryptoMarketCommand;
import net.epconsortium.cryptomarket.database.dao.InvestorDao;
import net.epconsortium.cryptomarket.finances.Economy;
import net.epconsortium.cryptomarket.finances.ExchangeRates;
import net.epconsortium.cryptomarket.listeners.PlayerListeners;
import net.epconsortium.cryptomarket.task.SaveInvestorsTask;
import net.epconsortium.cryptomarket.task.UpdateExchangeRatesTask;
import net.epconsortium.cryptomarket.task.UpdateRichersListTask;
import net.epconsortium.cryptomarket.ui.InventoryController;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

/**
 * Main class of the plugin
 * 
 * @author roinujnosde
 */
public class CryptoMarket extends JavaPlugin {

    private static CryptoMarket cm;
    private static boolean debug;
    private net.milkbowl.vault.economy.Economy econ = null;
    private UpdateExchangeRatesTask exchangeRatesTask;
    private SaveInvestorsTask saveInvestorsTask;
    private UpdateRichersListTask richersListTask;

    @Override
    public void onEnable() {
        cm = this;
        // Salvando configuração
        saveDefaultConfig();
        repairConfig();

        // Eventos
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(InventoryController.getInstance(), this);
        pluginManager.registerEvents(new PlayerListeners(this), this);

        //Comandos
		PluginCommand command = getCommand("cryptomarket");
        CryptoMarketCommand cmd = new CryptoMarketCommand(this);
        command.setExecutor(cmd);
        command.setTabCompleter(cmd);

        // Configurando Vault e economia
        if (!setupEconomy()) {
            warn("§4Vault and/or economy plugin not found. Disabling plugin...");
            pluginManager.disablePlugin(this);
            return;
        }

        debug = getConfig().getBoolean("debug", false);

        getInvestorDao().configureDatabase(this, (success) -> {
            if (!success) {
                getServer().getPluginManager().disablePlugin(this);
            } else {
                getLogger().info("Database configured successfuly!");

                getExchangeRates().updateAll();
                startTasks();
                if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                    new CMExpansion(this).register();
                }
                Collection<? extends Player> onlinePlayers = getServer().getOnlinePlayers();
                if (!onlinePlayers.isEmpty()) {
                    getLogger().info("Found players online (did you reload?), loading their data...");
                    onlinePlayers.forEach(getInvestorDao()::loadInvestor);
                }
            }
        });
    }

    private void startTasks() {
        (exchangeRatesTask = new UpdateExchangeRatesTask(this)).start();
        (saveInvestorsTask = new SaveInvestorsTask(this)).start();
        (richersListTask = new UpdateRichersListTask(this)).start();
    }

    /**
     * Reloads the config from disk, repairs any missing keys, refreshes cached
     * settings and reschedules recurring tasks so new intervals take effect.
     *
     * @return a short human-readable status message
     */
    public String reload() {
        StringBuilder status = new StringBuilder();
        try {
            reloadConfig();
        } catch (Exception ex) {
            // Corrupt YAML: back up and rewrite from jar defaults
            status.append("Corrupt config backed up; defaults restored. ");
            if (!rewriteConfigFromDefaults()) {
                status.append("Failed to restore defaults: ").append(ex.getMessage()).append(". ");
            }
            reloadConfig();
        }
        int repaired = repairConfig();
        if (repaired > 0) {
            status.append("Added ").append(repaired).append(" missing key(s). ");
        }
        debug = getConfig().getBoolean("debug", false);

        if (exchangeRatesTask != null) { exchangeRatesTask.cancel(); }
        if (saveInvestorsTask != null) { saveInvestorsTask.cancel(); }
        if (richersListTask != null) { richersListTask.cancel(); }
        startTasks();

        status.append("Config reloaded.");
        return status.toString();
    }

    /**
     * Fills in any missing keys in the on-disk config from the jar's bundled
     * config.yml, preserving all user values.
     *
     * @return the number of keys that were added
     */
    private int repairConfig() {
        InputStream resource = getResource("config.yml");
        if (resource == null) {
            return 0;
        }
        int added = 0;
        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            FileConfiguration cfg = getConfig();
            for (String key : defaults.getKeys(true)) {
                if (!cfg.contains(key)) {
                    cfg.set(key, defaults.get(key));
                    added++;
                }
            }
            if (added > 0) {
                cfg.options().copyDefaults(true);
                cfg.setDefaults(defaults);
                saveConfig();
                getLogger().info("Repaired config.yml: added " + added + " missing key(s).");
            }
        } catch (IOException e) {
            getLogger().warning("Could not repair config: " + e.getMessage());
        }
        return added;
    }

    /**
     * Backs up the current (presumed corrupt) config.yml and rewrites it from
     * the jar defaults.
     *
     * @return true if the rewrite succeeded
     */
    private boolean rewriteConfigFromDefaults() {
        File current = new File(getDataFolder(), "config.yml");
        if (current.exists()) {
            File backup = new File(getDataFolder(),
                    "config.yml.broken-" + System.currentTimeMillis());
            try {
                Files.move(current.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                getLogger().warning("Corrupt config.yml moved to " + backup.getName());
            } catch (IOException e) {
                getLogger().warning("Could not back up corrupt config.yml: " + e.getMessage());
                return false;
            }
        }
        try {
            saveResource("config.yml", false);
            return true;
        } catch (Exception e) {
            getLogger().warning("Could not restore default config.yml: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void onDisable() {
    	getServer().getScheduler().cancelTasks(this);
        getInvestorDao().saveAll();
    }

    /**
     * Sends a debug message to the console if debug is enabled
     *
     * @param message message
     */
    public static void debug(String message) {
        if (debug) {
            getInstance().getLogger().info(message);
        }
    }

    /**
     * Sends an warn to the console
     *
     * @param message message
     */
    public static void warn(String message) {
        getInstance().getLogger().warning(message);
    }

    /**
     * Configures Vault economy
     *
     * @return true if success
     */
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp = getServer()
                .getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public InvestorDao getInvestorDao() {
        return InvestorDao.getInstance(this);
    }

    public ExchangeRates getExchangeRates() {
        return ExchangeRates.getInstance(this);
    }

    /**
     * Returns Vault's Economy
     *
     * @return economy
     */
    public net.milkbowl.vault.economy.Economy getVaultEconomy() {
        return econ;
    }

    public Economy getEconomy() {
        return Economy.getInstance(this);
    }

    /**
     * Returns the instance of CryptoMarket
     *
     * @return the instance
     */
    public static CryptoMarket getInstance() {
        return cm;
    }
}

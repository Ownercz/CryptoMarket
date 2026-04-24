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
import org.bukkit.configuration.InvalidConfigurationException;
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
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
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

    @Override
    public void onEnable() {
        cm = this;
        // Salvando configuração
        saveDefaultConfig();

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
        new UpdateExchangeRatesTask(this).start();
        new SaveInvestorsTask(this).start();
        new UpdateRichersListTask(this).start();
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
     * Result of a reload attempt.
     */
    public enum ReloadResult {
        /** Config loaded fine and no missing keys had to be added. */
        OK,
        /** Config was valid, but missing keys were added from defaults. */
        MERGED,
        /** Config was corrupted and has been restored from the bundled defaults. */
        RESTORED,
        /** Reload failed completely. */
        FAILED
    }

    /**
     * Reloads the plugin configuration from disk. If the file on disk is
     * corrupted, a backup is created and the default config is written in its
     * place. Any missing keys found in the default config are added to the
     * user's config to avoid {@code null}/default-only lookups.
     *
     * @return the reload result
     */
    public ReloadResult reloadPluginConfig() {
        File configFile = new File(getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            saveDefaultConfig();
        }

        ReloadResult result = ReloadResult.OK;
        YamlConfiguration disk = new YamlConfiguration();
        try {
            disk.load(configFile);
        } catch (IOException | InvalidConfigurationException ex) {
            warn("Config file is corrupted, restoring defaults. Reason: " + ex.getMessage());
            if (!backupAndRestoreConfig(configFile)) {
                return ReloadResult.FAILED;
            }
            result = ReloadResult.RESTORED;
        }

        try {
            reloadConfig();
        } catch (Exception ex) {
            warn("Unexpected error while reloading config: " + ex.getMessage());
            return ReloadResult.FAILED;
        }

        if (result != ReloadResult.RESTORED) {
            if (mergeMissingDefaults()) {
                result = ReloadResult.MERGED;
            }
        }

        debug = getConfig().getBoolean("debug", false);
        return result;
    }

    /**
     * Adds to the user config any keys present in the bundled default config
     * but missing on disk, then saves if anything changed.
     *
     * @return true if at least one key was added
     */
    private boolean mergeMissingDefaults() {
        InputStream in = getResource("config.yml");
        if (in == null) {
            return false;
        }
        YamlConfiguration defaults;
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            defaults = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ex) {
            warn("Could not read bundled default config: " + ex.getMessage());
            return false;
        }

        FileConfiguration current = getConfig();
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                continue;
            }
            if (!current.isSet(key)) {
                current.set(key, defaults.get(key));
                changed = true;
            }
        }
        if (changed) {
            saveConfig();
            getLogger().info("Added missing config keys from defaults.");
        }
        return changed;
    }

    /**
     * Renames the corrupted config to a timestamped backup and writes the
     * bundled default config in its place.
     *
     * @param configFile the config file on disk
     * @return true if the default config was restored successfully
     */
    private boolean backupAndRestoreConfig(File configFile) {
        try {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            File backup = new File(configFile.getParentFile(), "config.yml.broken-" + stamp);
            Files.move(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            getLogger().warning("Corrupted config backed up to " + backup.getName());
        } catch (IOException ex) {
            warn("Failed to back up corrupted config: " + ex.getMessage());
            // fall through and try to overwrite anyway
        }
        try {
            saveResource("config.yml", true);
            return true;
        } catch (Exception ex) {
            warn("Failed to restore default config: " + ex.getMessage());
            return false;
        }
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

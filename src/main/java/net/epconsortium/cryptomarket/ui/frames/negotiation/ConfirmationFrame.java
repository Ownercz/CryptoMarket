package net.epconsortium.cryptomarket.ui.frames.negotiation;

import com.cryptomorin.xseries.XMaterial;
import net.epconsortium.cryptomarket.database.dao.Investor;
import net.epconsortium.cryptomarket.finances.Economy;
import net.epconsortium.cryptomarket.finances.ExchangeRate;
import net.epconsortium.cryptomarket.finances.Negotiation;
import net.epconsortium.cryptomarket.ui.Component;
import net.epconsortium.cryptomarket.ui.ComponentImpl;
import net.epconsortium.cryptomarket.ui.Frame;
import net.epconsortium.cryptomarket.ui.InventoryDrawer;
import net.epconsortium.cryptomarket.util.Formatter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static net.epconsortium.cryptomarket.ui.Components.addPanels;

/**
 * Final step: shows a summary and executes the trade via Economy on confirm.
 */
public class ConfirmationFrame extends Frame {

    private final Negotiation negotiation;
    private final String coin;
    private final BigDecimal amount;

    public ConfirmationFrame(@Nullable Frame parent, @NotNull Player viewer,
                             @NotNull Negotiation negotiation, @NotNull String coin,
                             @NotNull BigDecimal amount) {
        super(parent, viewer);
        this.negotiation = negotiation;
        this.coin = coin;
        this.amount = amount;
    }

    @Override
    public @NotNull String getTitle() {
        return configuration.getNegotiationConfirmFrameName();
    }

    @Override
    public int getSize() {
        return 27;
    }

    @Override
    public void createComponents() {
        add(summaryItem());
        add(confirmButton());
        add(cancelButton());
        add(backButton());
        addGlasses();
    }

    private Component summaryItem() {
        Economy economy = plugin.getEconomy();
        BigDecimal gross = economy.convert(coin, amount);
        String currency = configuration.getPhysicalCurrency();

        List<String> rawLore;
        double flatFee = 0;
        double pctFee = 0;
        BigDecimal net = gross;
        if (negotiation == Negotiation.SELL
                && (configuration.getWithdrawalFlatFee() > 0
                    || configuration.getWithdrawalPercentageFee() > 0)) {
            flatFee = configuration.getWithdrawalFlatFee();
            pctFee = configuration.getWithdrawalPercentageFee();
            double netD = (gross.doubleValue() - flatFee) * (1 - pctFee / 100d);
            if (netD < 0) netD = 0;
            net = BigDecimal.valueOf(netD);
            rawLore = configuration.getNegotiationConfirmSummaryLoreSell();
        } else {
            rawLore = configuration.getNegotiationConfirmSummaryLore();
        }

        List<String> lore = new ArrayList<>();
        for (String line : rawLore) {
            lore.add(MessageFormat.format(line,
                    Formatter.formatServerCurrency(gross),
                    currency,
                    Formatter.formatServerCurrency(flatFee),
                    Formatter.formatPercentage(pctFee),
                    Formatter.formatServerCurrency(net)));
        }

        String action = negotiation == Negotiation.PURCHASE
                ? configuration.getActionBuy() : configuration.getActionSell();
        String name = MessageFormat.format(configuration.getNegotiationConfirmSummaryName(),
                action, amount.toPlainString(), coin);

        return new ComponentImpl.Builder(XMaterial.PAPER)
                .withDisplayName(name)
                .withLore(lore)
                .withSlot(13)
                .build();
    }

    private Component confirmButton() {
        Component c = new ComponentImpl.Builder(XMaterial.EMERALD_BLOCK)
                .withDisplayName(configuration.getNegotiationConfirmButtonName())
                .withSlot(11)
                .build();
        c.setListener(ClickType.LEFT, this::execute);
        return c;
    }

    private Component cancelButton() {
        Component c = new ComponentImpl.Builder(XMaterial.REDSTONE_BLOCK)
                .withDisplayName(configuration.getNegotiationCancelButtonName())
                .withSlot(15)
                .build();
        c.setListener(ClickType.LEFT, () -> getViewer().closeInventory());
        return c;
    }

    private Component backButton() {
        Component c = new ComponentImpl.Builder(XMaterial.ARROW)
                .withDisplayName(configuration.getNegotiationBackButtonName())
                .withSlot(22)
                .build();
        c.setListener(ClickType.LEFT, () -> InventoryDrawer.getInstance().open(
                new AmountFrame(getParent() != null ? getParent().getParent() : null,
                        getViewer(), negotiation, coin, amount)));
        return c;
    }

    private void execute() {
        Player viewer = getViewer();
        viewer.closeInventory();

        ExchangeRate rate = plugin.getExchangeRates().getExchangeRate(LocalDate.now());
        if (rate == null || rate.getCoinValue(coin).compareTo(BigDecimal.ZERO) <= 0) {
            viewer.sendMessage(configuration.getMessageOutdatedData());
            return;
        }

        Investor investor = plugin.getInvestorDao().getInvestor(viewer);
        if (investor == null) {
            viewer.sendMessage(configuration.getMessageErrorConnectingToDatabase());
            return;
        }

        Economy economy = plugin.getEconomy();
        boolean success;
        if (negotiation == Negotiation.PURCHASE) {
            success = economy.buy(coin, investor, amount);
        } else {
            success = economy.sell(coin, investor, amount);
        }

        if (success) {
            viewer.sendMessage(configuration.getMessageSuccessfulNegotiation());
        } else {
            viewer.sendMessage(configuration.getMessageErrorNotEnoughBalance());
        }
    }

    private void addGlasses() {
        int[] taken = {11, 13, 15, 22};
        List<Integer> panels = new ArrayList<>();
        outer:
        for (int i = 0; i < getSize(); i++) {
            for (int t : taken) {
                if (t == i) continue outer;
            }
            panels.add(i);
        }
        int[] arr = new int[panels.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = panels.get(i);
        addPanels(this, XMaterial.GRAY_STAINED_GLASS_PANE, arr);
    }
}

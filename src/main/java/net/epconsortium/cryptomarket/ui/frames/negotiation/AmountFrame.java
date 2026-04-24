package net.epconsortium.cryptomarket.ui.frames.negotiation;

import com.cryptomorin.xseries.XMaterial;
import net.epconsortium.cryptomarket.finances.Economy;
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
import java.util.ArrayList;
import java.util.List;

import static net.epconsortium.cryptomarket.ui.Components.addPanels;

/**
 * Third step of the GUI flow: adjust the amount to trade using +/- buttons.
 */
public class AmountFrame extends Frame {

    private static final int[] STEPS = {1000, 100, 10, 1};
    private static final int[] DECREMENT_SLOTS = {11, 12, 13, 14}; // -1000, -100, -10, -1
    private static final int[] INCREMENT_SLOTS = {30, 31, 32, 33}; // +1, +10, +100, +1000
    private static final int INFO_SLOT = 22;
    private static final int BACK_SLOT = 48;
    private static final int CONFIRM_SLOT = 50;

    private final Negotiation negotiation;
    private final String coin;
    private final BigDecimal amount;

    public AmountFrame(@Nullable Frame parent, @NotNull Player viewer,
                       @NotNull Negotiation negotiation, @NotNull String coin) {
        this(parent, viewer, negotiation, coin, BigDecimal.ZERO);
    }

    public AmountFrame(@Nullable Frame parent, @NotNull Player viewer,
                       @NotNull Negotiation negotiation, @NotNull String coin,
                       @NotNull BigDecimal amount) {
        super(parent, viewer);
        this.negotiation = negotiation;
        this.coin = coin;
        this.amount = amount.max(BigDecimal.ZERO);
    }

    @Override
    public @NotNull String getTitle() {
        String action = negotiation == Negotiation.PURCHASE
                ? configuration.getActionBuy() : configuration.getActionSell();
        return MessageFormat.format(configuration.getNegotiationAmountFrameName(), action, coin);
    }

    @Override
    public int getSize() {
        return 54;
    }

    @Override
    public void createComponents() {
        add(infoItem());
        // Decrements: -1000, -100, -10, -1
        for (int i = 0; i < STEPS.length; i++) {
            add(stepButton(STEPS[i], DECREMENT_SLOTS[i], false));
        }
        // Increments: +1, +10, +100, +1000
        for (int i = 0; i < STEPS.length; i++) {
            add(stepButton(STEPS[STEPS.length - 1 - i], INCREMENT_SLOTS[i], true));
        }
        add(confirmButton());
        add(backButton());
        addGlasses();
    }

    private Component infoItem() {
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
            rawLore = configuration.getNegotiationAmountInfoLoreSell();
        } else {
            rawLore = configuration.getNegotiationAmountInfoLore();
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

        String name = MessageFormat.format(configuration.getNegotiationAmountInfoName(),
                amount.toPlainString(), coin);

        return new ComponentImpl.Builder(XMaterial.PAPER)
                .withDisplayName(name)
                .withLore(lore)
                .withSlot(INFO_SLOT)
                .build();
    }

    private Component stepButton(int step, int slot, boolean increment) {
        XMaterial mat = increment ? XMaterial.LIME_DYE : XMaterial.RED_DYE;
        String template = increment
                ? configuration.getNegotiationIncrementButtonName()
                : configuration.getNegotiationDecrementButtonName();
        String name = MessageFormat.format(template, step);

        Component c = new ComponentImpl.Builder(mat)
                .withDisplayName(name)
                .withSlot(slot)
                .withAmount(Math.min(64, Math.max(1, step == 1 ? 1 : step / 10)))
                .build();
        BigDecimal delta = BigDecimal.valueOf(step);
        BigDecimal next = increment ? amount.add(delta) : amount.subtract(delta);
        c.setListener(ClickType.LEFT, () -> InventoryDrawer.getInstance().open(
                new AmountFrame(getParent(), getViewer(), negotiation, coin, next)));
        return c;
    }

    private Component confirmButton() {
        boolean enabled = amount.compareTo(BigDecimal.ZERO) > 0;
        XMaterial mat = enabled ? XMaterial.EMERALD_BLOCK : XMaterial.GRAY_CONCRETE;
        Component c = new ComponentImpl.Builder(mat)
                .withDisplayName(configuration.getNegotiationConfirmButtonName())
                .withSlot(CONFIRM_SLOT)
                .build();
        if (enabled) {
            c.setListener(ClickType.LEFT, () -> InventoryDrawer.getInstance().open(
                    new ConfirmationFrame(this, getViewer(), negotiation, coin, amount)));
        }
        return c;
    }

    private Component backButton() {
        Component c = new ComponentImpl.Builder(XMaterial.ARROW)
                .withDisplayName(configuration.getNegotiationBackButtonName())
                .withSlot(BACK_SLOT)
                .build();
        c.setListener(ClickType.LEFT, () -> InventoryDrawer.getInstance().open(getParent()));
        return c;
    }

    private void addGlasses() {
        List<Integer> taken = new ArrayList<>();
        taken.add(INFO_SLOT);
        taken.add(CONFIRM_SLOT);
        taken.add(BACK_SLOT);
        for (int s : DECREMENT_SLOTS) taken.add(s);
        for (int s : INCREMENT_SLOTS) taken.add(s);

        List<Integer> panels = new ArrayList<>();
        for (int i = 0; i < getSize(); i++) {
            if (!taken.contains(i)) panels.add(i);
        }
        int[] arr = new int[panels.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = panels.get(i);
        addPanels(this, XMaterial.GRAY_STAINED_GLASS_PANE, arr);
    }
}

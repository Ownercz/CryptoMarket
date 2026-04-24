package net.epconsortium.cryptomarket.ui.frames.negotiation;

import com.cryptomorin.xseries.XMaterial;
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
 * Second step of the GUI flow: pick a cryptocoin to trade.
 */
public class CoinSelectionFrame extends Frame {

    private final Negotiation negotiation;

    public CoinSelectionFrame(@Nullable Frame parent, @NotNull Player viewer,
                              @NotNull Negotiation negotiation) {
        super(parent, viewer);
        this.negotiation = negotiation;
    }

    @Override
    public @NotNull String getTitle() {
        return configuration.getNegotiationCoinFrameName();
    }

    @Override
    public int getSize() {
        return configuration.getCoins().size() > 7 ? 54 : 27;
    }

    @Override
    public void createComponents() {
        List<String> coins = configuration.getCoins();
        ExchangeRate rate = plugin.getExchangeRates().getExchangeRate(LocalDate.now());

        int[] slots = coinSlots(coins.size());
        for (int i = 0; i < coins.size() && i < slots.length; i++) {
            add(coinButton(coins.get(i), rate, slots[i]));
        }
        add(backButton());
        addGlasses(slots);
    }

    private Component coinButton(String coin, @Nullable ExchangeRate rate, int slot) {
        BigDecimal value = rate != null ? rate.getCoinValue(coin) : null;
        boolean available = value != null && value.compareTo(BigDecimal.ZERO) > 0;

        String name = MessageFormat.format(configuration.getNegotiationCoinButtonName(), coin);
        List<String> lore = new ArrayList<>();
        if (available) {
            for (String line : configuration.getNegotiationCoinButtonLore()) {
                lore.add(MessageFormat.format(line, Formatter.formatServerCurrency(value),
                        configuration.getPhysicalCurrency()));
            }
        } else {
            lore.add(configuration.getNegotiationCoinButtonUnavailable());
        }

        Component c = new ComponentImpl.Builder(XMaterial.SUNFLOWER)
                .withDisplayName(name)
                .withLore(lore)
                .withSlot(slot)
                .build();

        if (available) {
            c.setPermission(ClickType.LEFT, "cryptomarket.negotiate");
            c.setListener(ClickType.LEFT, () -> InventoryDrawer.getInstance().open(
                    new AmountFrame(this, getViewer(), negotiation, coin)));
        }
        return c;
    }

    private Component backButton() {
        int slot = getSize() - 5;
        Component c = new ComponentImpl.Builder(XMaterial.ARROW)
                .withDisplayName(configuration.getNegotiationBackButtonName())
                .withSlot(slot)
                .build();
        c.setListener(ClickType.LEFT, () -> InventoryDrawer.getInstance().open(getParent()));
        return c;
    }

    /**
     * Distributes coin buttons across one or two rows centred in the frame.
     */
    private int[] coinSlots(int count) {
        // Row starting slots: 10 (first inner row) and 19 (second inner row)
        int[] rowStart = getSize() == 27 ? new int[]{10} : new int[]{10, 19};
        int perRow = 7;
        int[] slots = new int[Math.min(count, rowStart.length * perRow)];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = rowStart[i / perRow] + (i % perRow);
        }
        return slots;
    }

    private void addGlasses(int[] coinSlots) {
        int backSlot = getSize() - 5;
        List<Integer> taken = new ArrayList<>();
        for (int s : coinSlots) {
            taken.add(s);
        }
        taken.add(backSlot);

        List<Integer> panels = new ArrayList<>();
        for (int i = 0; i < getSize(); i++) {
            if (!taken.contains(i)) {
                panels.add(i);
            }
        }
        int[] arr = new int[panels.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = panels.get(i);
        }
        addPanels(this, XMaterial.GRAY_STAINED_GLASS_PANE, arr);
    }
}

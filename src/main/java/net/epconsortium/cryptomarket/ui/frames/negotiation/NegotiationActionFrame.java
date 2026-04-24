package net.epconsortium.cryptomarket.ui.frames.negotiation;

import com.cryptomorin.xseries.XMaterial;
import net.epconsortium.cryptomarket.finances.Negotiation;
import net.epconsortium.cryptomarket.ui.Component;
import net.epconsortium.cryptomarket.ui.ComponentImpl;
import net.epconsortium.cryptomarket.ui.Frame;
import net.epconsortium.cryptomarket.ui.InventoryDrawer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static net.epconsortium.cryptomarket.ui.Components.addPanels;

/**
 * First step of the GUI negotiation flow: lets the player choose between
 * buying and selling.
 */
public class NegotiationActionFrame extends Frame {

    public NegotiationActionFrame(@Nullable Frame parent, @NotNull Player viewer) {
        super(parent, viewer);
    }

    @Override
    public @NotNull String getTitle() {
        return configuration.getNegotiationActionFrameName();
    }

    @Override
    public int getSize() {
        return 27;
    }

    @Override
    public void createComponents() {
        add(buyButton());
        add(sellButton());
        if (getParent() != null) {
            add(backButton());
        }
        addGlasses();
    }

    private Component buyButton() {
        Component c = new ComponentImpl.Builder(XMaterial.EMERALD)
                .withDisplayName(configuration.getNegotiationBuyButtonName())
                .withLore(configuration.getNegotiationBuyButtonLore())
                .withSlot(11)
                .build();
        c.setPermission(ClickType.LEFT, "cryptomarket.negotiate");
        c.setListener(ClickType.LEFT, () -> InventoryDrawer.getInstance().open(
                new CoinSelectionFrame(this, getViewer(), Negotiation.PURCHASE)));
        return c;
    }

    private Component sellButton() {
        Component c = new ComponentImpl.Builder(XMaterial.REDSTONE)
                .withDisplayName(configuration.getNegotiationSellButtonName())
                .withLore(configuration.getNegotiationSellButtonLore())
                .withSlot(15)
                .build();
        c.setPermission(ClickType.LEFT, "cryptomarket.negotiate");
        c.setListener(ClickType.LEFT, () -> InventoryDrawer.getInstance().open(
                new CoinSelectionFrame(this, getViewer(), Negotiation.SELL)));
        return c;
    }

    private Component backButton() {
        Component c = new ComponentImpl.Builder(XMaterial.ARROW)
                .withDisplayName(configuration.getNegotiationBackButtonName())
                .withSlot(22)
                .build();
        c.setListener(ClickType.LEFT, () -> InventoryDrawer.getInstance().open(getParent()));
        return c;
    }

    private void addGlasses() {
        int[] slots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 14, 16, 17,
                18, 19, 20, 21, 23, 24, 25, 26};
        addPanels(this, XMaterial.GRAY_STAINED_GLASS_PANE, slots);
    }
}

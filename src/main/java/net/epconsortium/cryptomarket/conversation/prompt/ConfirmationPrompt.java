package net.epconsortium.cryptomarket.conversation.prompt;

import net.epconsortium.cryptomarket.CryptoMarket;
import net.epconsortium.cryptomarket.finances.Economy;
import net.epconsortium.cryptomarket.finances.Negotiation;
import net.epconsortium.cryptomarket.database.dao.Investor;
import net.epconsortium.cryptomarket.util.Configuration;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.FixedSetPrompt;
import org.bukkit.conversations.Prompt;

import java.math.BigDecimal;
import java.text.MessageFormat;

/**
 * Prompt that asks the player to confirm the negotiation
 * 
 * @author roinujnosde
 */
public class ConfirmationPrompt extends FixedSetPrompt {

    private final String cancel;

    public ConfirmationPrompt(String yes, String cancel) {
        super(yes, cancel);
        this.cancel = cancel;
    }

    @Override
    protected Prompt acceptValidatedInput(ConversationContext context, String s) {
        if (s.equals(cancel)) {
            return END_OF_CONVERSATION;
        }

        CryptoMarket plugin = (CryptoMarket) context.getPlugin();
        String coin = (String) context.getSessionData("coin");
        Economy economy = plugin.getEconomy();
        Investor investor = (Investor) context.getSessionData("investor");
        
        BigDecimal value = (BigDecimal) context.getSessionData("amount");

        Negotiation negotiation = getNegotiation(context);
        switch (negotiation) {
            case PURCHASE:
                if (!economy.buy(coin, investor, value)) {
                    return new ErrorPrompt();
                }
                break;
            case SELL:
                if (!economy.sell(coin, investor, value)) {
                    return new ErrorPrompt();
                }
                break;
        }

        return new SuccessPrompt();
    }

    @Override
    public String getPromptText(ConversationContext context) {
        CryptoMarket plugin = (CryptoMarket) context.getPlugin();
        String coin = (String) context.getSessionData("coin");
        Configuration config = new Configuration(plugin);
        String message = config.getMessageNegotiationConfirmation();
        String action = null;

        Negotiation negotiation = getNegotiation(context);
        switch (negotiation) {
            case SELL:
                action = config.getActionSell();
                break;
            case PURCHASE:
                action = config.getActionBuy();
                break;
        }

        BigDecimal amount = ((BigDecimal) context.getSessionData("amount"));
        BigDecimal value = plugin.getEconomy().convert(coin, amount);

        String promptText = MessageFormat.format(message, action, amount, coin, value);

        if (negotiation == Negotiation.SELL) {
            double flatFee = config.getWithdrawalFlatFee();
            double percentageFee = config.getWithdrawalPercentageFee();
            if (flatFee > 0 || percentageFee > 0) {
                double net = (value.doubleValue() - flatFee) * (1 - percentageFee / 100d);
                if (net < 0) {
                    net = 0;
                }
                promptText += MessageFormat.format(config.getMessageWithdrawalFeeInfo(),
                        flatFee, percentageFee, net);
            }
        }

        return promptText + " " + formatFixedSet();
    }

    private Negotiation getNegotiation(ConversationContext context) {
        return (Negotiation) context.getSessionData("negotiation");
    }
}

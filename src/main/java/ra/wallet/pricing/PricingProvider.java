package ra.wallet.pricing;

import ra.common.currency.Coin;

import java.util.List;

public interface PricingProvider {
    boolean tracksSymbol(String symbol);
    List<Coin> getPrices(String content);
}

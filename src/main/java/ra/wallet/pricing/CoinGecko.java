package ra.wallet.pricing;

import ra.common.Envelope;
import ra.common.currency.BaseCoin;
import ra.common.currency.Coin;
import ra.wallet.WalletService;
import ra.util.JSONParser;
import ra.util.tasks.TaskRunner;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class CoinGecko extends BasePricingProvider {

    private static final Logger LOG = Logger.getLogger(CoinGecko.class.getName());

    private WalletService service;
    private URL host;

    private List<String> symbolsTracked = Arrays.asList("BTC","USD");

    public CoinGecko(WalletService service, TaskRunner taskRunner) {
        super(CoinGecko.class.getSimpleName(), taskRunner);
        try {
            host = new URL("https://api.coingecko.com/api/v3/exchange_rates");
            this.service = service;
        } catch (MalformedURLException e) {
            LOG.severe(e.getLocalizedMessage());
        }
    }

    @Override
    public boolean tracksSymbol(String symbol) {
        return symbolsTracked.contains(symbol);
    }

    @Override
    public Boolean execute() {
        Envelope e = Envelope.documentFactory();
        e.setURL(host);
        e.addNVP("provider", CoinGecko.class.getSimpleName());
        e.addRoute(WalletService.class.getName(), WalletService.OPERATION_UPDATE_PRICES);
        e.addRoute("TORClientService", "SEND");
        return service.send(e);
    }

    @Override
    public List<Coin> getPrices(String content) {
        List<Coin> coins = new ArrayList<>();
        Map<String,Object> payload = (Map<String,Object>) JSONParser.parse(content);
        Map<String,Object> rates = (Map<String,Object>)payload.get("rates");
        Coin coin = null;
        for(String symbol : rates.keySet()) {
            Map<String,Object> coinMap = (Map<String,Object>)rates.get(symbol);
            Double value = (double)coinMap.get("value");
            String coinType = (String)coinMap.get("type");
            try {
                switch (coinType) {
                    case "crypto": {
                        coin = (Coin) Class.forName("ra.common.currency.crypto." + symbol.toUpperCase()).getConstructor().newInstance();
                        break;
                    }
                    case "fiat": {
                        coin = (Coin) Class.forName("ra.common.currency.fiat." + symbol.toUpperCase()).getConstructor().newInstance();
                        break;
                    }
                    case "commodity": {
                        coin = (Coin) Class.forName("ra.common.currency.commodity." + symbol.toUpperCase()).getConstructor().newInstance();
                    }
                }
            } catch (Exception ex) {
                LOG.warning(ex.getLocalizedMessage());
                continue;
            }
            if(coin!=null) {
                ((BaseCoin)coin).setValue(value);
                coins.add(coin);
            }
        }
        return coins;
    }
}

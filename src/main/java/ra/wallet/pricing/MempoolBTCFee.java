package ra.wallet.pricing;

import ra.common.Envelope;
import ra.common.currency.Coin;
import ra.common.currency.crypto.BTC;
import ra.wallet.WalletService;
import ra.util.JSONParser;
import ra.util.tasks.TaskRunner;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class MempoolBTCFee extends BasePricingProvider {

    private static Logger LOG = Logger.getLogger(MempoolBTCFee.class.getName());

    // Miner fees are normally 1-600 sat/vbyte.
    // DEFAULT_BTC_TX_FEE is only used if mempool will not deliver the fee and is conservative to be safe.
    private static final long DEFAULT_BTC_TX_FEE = 150;

    private WalletService service;
    private URL host1;
    private URL host2;
    private URL host3;

    public MempoolBTCFee(WalletService service, TaskRunner taskRunner) {
        super(MempoolBTCFee.class.getSimpleName(), taskRunner);
        try {
            host1 = new URL("https://mempool.space/api/v1/fees/recommended");
            host2 = new URL("https://mempool.emzy.de/api/v1/fees/recommended");
            host3 = new URL("https://mempool.bisq.services/api/v1/fees/recommended");
            this.service = service;
        } catch (MalformedURLException e) {
            LOG.severe(e.getLocalizedMessage());
        }
    }

    @Override
    public boolean tracksSymbol(String symbol) {
        return "BTC-Fee-High".equals(symbol) || "BTC-Fee-Medium".equals(symbol) || "BTC-Fee-Low".equals(symbol);
    }

    @Override
    public List<Coin> getPrices(String content) {
        List<Coin> coins = new ArrayList<>();
        Map<String,Object> m = (Map<String,Object>) JSONParser.parse(content);
        BTC coin = new BTC();
        coin.setHighFee((double)m.get("fastestFee"));
        coin.setMediumFee((double)m.get("halfHourFee"));
        coin.setLowFee((double)m.get("hourFee"));
        coins.add(coin);
        return coins;
    }

    @Override
    public Boolean execute() {
        Envelope e1 = Envelope.documentFactory();
        e1.setURL(host1);
        e1.addNVP("provider", MempoolBTCFee.class.getSimpleName());
        e1.addRoute(WalletService.class.getName(), WalletService.OPERATION_UPDATE_PRICES);
        e1.addRoute("TORClientService", "SEND");
        e1.ratchet();
        boolean success1 = service.send(e1);

        Envelope e2 = Envelope.documentFactory();
        e2.setURL(host2);
        e2.addNVP("provider", MempoolBTCFee.class.getSimpleName());
        e2.addRoute(WalletService.class.getName(), WalletService.OPERATION_UPDATE_PRICES);
        e2.addRoute("TORClientService", "SEND");
        e2.ratchet();
        boolean success2 = service.send(e2);

        Envelope e3 = Envelope.documentFactory();
        e3.setURL(host3);
        e3.addNVP("provider", MempoolBTCFee.class.getSimpleName());
        e3.addRoute(WalletService.class.getName(), WalletService.OPERATION_UPDATE_PRICES);
        e3.addRoute("TORClientService", "SEND");
        e3.ratchet();
        return service.send(e1) && success2 && success1;
    }
}

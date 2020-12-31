package ra.wallet.pricing;

import ra.common.Envelope;
import ra.common.currency.Coin;
import ra.wallet.WalletService;
import ra.util.tasks.TaskRunner;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class LiraRate extends BasePricingProvider {

    private static Logger LOG = Logger.getLogger(LiraRate.class.getName());

    private URL host;

    private WalletService service;

    public LiraRate(WalletService service, TaskRunner taskRunner) {
        super(LiraRate.class.getSimpleName(), taskRunner);
        this.service = service;
        try {
            host = new URL("https://lirarate.com/");
            this.service = service;
        } catch (MalformedURLException e) {
            LOG.severe(e.getLocalizedMessage());
        }
    }

    @Override
    public boolean tracksSymbol(String symbol) {
        return "LBP".equals(symbol);
    }

    @Override
    public List<Coin> getPrices(String content) {
        List<Coin> coins = new ArrayList<>();
        // TODO: scrape USD/LBP from HTML
        return coins;
    }

    @Override
    public Boolean execute() {
        // Retrieve HTML from site
        Envelope e = Envelope.documentFactory();
        e.setURL(host);
        e.addNVP("provider", LiraRate.class.getSimpleName());
        e.addRoute(WalletService.class.getName(), WalletService.OPERATION_UPDATE_PRICES);
        e.addRoute("TORClientService", "SEND");
        return service.send(e);
    }
}

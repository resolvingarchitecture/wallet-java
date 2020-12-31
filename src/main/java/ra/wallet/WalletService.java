package ra.wallet;

import ra.common.Envelope;
import ra.common.currency.BaseCoin;
import ra.common.currency.Coin;
import ra.common.currency.crypto.Crypto;
import ra.common.messaging.MessageProducer;
import ra.common.route.Route;
import ra.common.service.BaseService;
import ra.common.service.ServiceStatus;
import ra.common.service.ServiceStatusObserver;
import ra.wallet.pricing.CoinGecko;
import ra.wallet.pricing.LiraRate;
import ra.wallet.pricing.MempoolBTCFee;
import ra.wallet.pricing.PricingProvider;
import ra.util.tasks.TaskRunner;

import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

public class WalletService extends BaseService {

    private static final Logger LOG = Logger.getLogger(WalletService.class.getName());

    // BTC to LTN (BTC Multi-Sig)
    public static final String OPERATION_OPEN_PREPAID_TAB = "OPEN_PREPAID_TAB"; // Open Payment Channel: BTC -> BTC MS
    // Request
    public static final String OPERATION_MAKE_INVOICE = "MAKE_INVOICE"; // Request Payment from Payment Channel
    // BTC Multi-Sig -> BTC Payment
    public static final String OPERATION_PAY_INVOICE = "PAY_INVOICE"; // Spend from Payment Channel: BTC MS
    // BTC Multi-Sig -> BTC
    public static final String OPERATION_CLOSE_PREPAID_TAB = "CLOSE_PREPAID_TAB"; // Close Payment Channel: BTC MS -> BTC

    // v1.0 is just Fiat <-> BTC; v2.0 adds a round of XMR to BTC to ensure privacy regained if lost
    // v1.0 Fiat -> BTC
    // v2.0 Fiat -> BTC -> XMR -> BTC
    public static final String OPERATION_FIAT_TO_SAVINGS = "FIAT_TO_SAVINGS";
    // v1.0 BTC -> Fiat
    // v2.0 BTC -> XMR -> BTC -> Fiat
    public static final String OPERATION_SAVINGS_TO_FIAT = "SAVINGS_TO_FIAT";
    // v1.0 BTC -> Flash Drive
    // v2.0 BTC -> XMR -> BTC -> Flash Drive
    public static final String OPERATION_SAVINGS_TO_VAULT = "SAVINGS_TO_VAULT";
    // v1.0 Flash Drive -> BTC
    // v2.0 Flash Drive -> BTC -> XMR -> BTC
    public static final String OPERATION_VAULT_TO_SAVINGS = "VAULT_TO_SAVINGS";


    public static final String OPERATION_REQUEST_PRICE = "REQUEST_PRICE";
    public static final String OPERATION_UPDATE_PRICES = "UPDATE_PRICES";

    private static Long priceWindowMs = 5 * 60 * 1000L;

    private TaskRunner taskRunner;

    private Map<String, PricingProvider> providers = new HashMap<>();
    private Map<Long, List<Coin>> windowedCoins = new HashMap<>();
    private Map<String, Coin> averagedWindowedPrices = new HashMap<>();

    public WalletService() {
    }

    public WalletService(MessageProducer producer, ServiceStatusObserver observer) {
        super(producer, observer);
    }

    @Override
    public void handleDocument(Envelope e) {
        Route route = e.getRoute();
        String operation = route.getOperation();
        switch(operation) {
            case OPERATION_REQUEST_PRICE: {
                Object obj = e.getValue("symbol");
                if(obj==null) {
                    e.addErrorMessage("symbol in nvp param required");
                    return;
                }
                String symbol = (String)obj;
                Coin coin = averagedWindowedPrices.get(symbol);
                if(coin==null) {
                    e.addNVP("price","Not Yet Available");
                } else {
                    e.addNVP("price", coin.value());
                }
                break;
            }
            case OPERATION_UPDATE_PRICES: {
                if(e.getContent()!=null && e.getValue("provider")!=null) {
                    PricingProvider provider = providers.get(e.getValue("provider"));
                    if(provider!=null) {
                        updateGlobalPrices(provider.getPrices(new String((byte[]) e.getContent())));
                    }
                }
                break;
            }
            default: deadLetter(e); // Operation not supported
        }
    }

    private void updateGlobalPrices(List<Coin> coins) {
        long now = Instant.now().toEpochMilli();
        for(long key : windowedCoins.keySet()) {
            if((now - key) > priceWindowMs) {
                windowedCoins.remove(key);
            }
        }
        windowedCoins.put(now, coins);
        Map<String,List<Coin>> groupedPrices = new HashMap<>();
        for(List<Coin> cs : windowedCoins.values()) {
            for(Coin c : cs) {
                if(groupedPrices.get(c.symbol())==null) {
                    groupedPrices.put(c.symbol(), new ArrayList<>());
                }
                groupedPrices.get(c.symbol()).add(c);
            }
        }
        for(String symbol : groupedPrices.keySet()) {

            double totalValue = 0.00;
            double totalHighFee = 0.00;
            double totalMediumFee = 0.00;
            double totalLowFee = 0.00;

            int numberValueCoins = 0;
            int numberHighFeeCoins = 0;
            int numberMediumFeeCoins = 0;
            int numberLowFeeCoins = 0;

            for(Coin coin : groupedPrices.get(symbol)) {
                if(coin.value() > 0) {
                    totalValue += coin.value();
                    numberValueCoins++;
                }
                if(coin instanceof Crypto) {
                    Crypto cCoin = (Crypto) coin;
                    if(cCoin.getHighFee() > 0) {
                        totalHighFee += cCoin.getHighFee();
                        numberHighFeeCoins++;
                    }
                    if(cCoin.getMediumFee() > 0) {
                        totalMediumFee += cCoin.getMediumFee();
                        numberMediumFeeCoins++;
                    }
                    if(cCoin.getLowFee() > 0) {
                        totalLowFee += cCoin.getLowFee();
                        numberLowFeeCoins++;
                    }
                }
            }
            double avgValue = totalValue / numberValueCoins;
            double avgHighFee = totalHighFee / numberHighFeeCoins;
            double avgMediumFee = totalMediumFee / numberMediumFeeCoins;
            double avgLowFee = totalLowFee / numberLowFeeCoins;
            Coin coin;
            try {
                if(groupedPrices.get(symbol).getClass().getName().contains(".crypto.")) {
                    coin = (Coin)Class.forName("ra.common.currency.crypto."+symbol).getConstructor().newInstance();
                } else {
                    coin = (Coin)Class.forName("ra.common.currency.fiat."+symbol).getConstructor().newInstance();
                }
            } catch (Exception e) {
                LOG.warning(e.getLocalizedMessage());
                continue;
            }
            ((BaseCoin)coin).setValue(avgValue);
            if(coin instanceof Crypto) {
                Crypto cCoin = (Crypto) coin;
                cCoin.setHighFee(avgHighFee);
                cCoin.setMediumFee(avgMediumFee);
                cCoin.setLowFee(avgLowFee);
            }
            averagedWindowedPrices.put(symbol, coin);
        }
    }

    @Override
    public boolean start(Properties p) {
        LOG.info("Starting....");
        updateStatus(ServiceStatus.STARTING);

        taskRunner = new TaskRunner(1,1);

        CoinGecko coinGecko = new CoinGecko(this, taskRunner);
        coinGecko.setDelayed(true);
        coinGecko.setDelayTimeMS(5 * 1000L);
        coinGecko.setLongRunning(true);
        coinGecko.setPeriodicity(5 * 60 * 1000L);
        taskRunner.addTask(coinGecko);
        providers.put(CoinGecko.class.getSimpleName(), coinGecko);

        LiraRate liraRate = new LiraRate(this, taskRunner);
        liraRate.setLongRunning(true);
        liraRate.setPeriodicity(5 * 60 * 1000L);
        taskRunner.addTask(liraRate);
        providers.put(LiraRate.class.getSimpleName(), liraRate);

        MempoolBTCFee mempoolBTCFee = new MempoolBTCFee(this, taskRunner);
        mempoolBTCFee.setDelayed(true);
        mempoolBTCFee.setDelayTimeMS(10 * 1000L);
        mempoolBTCFee.setLongRunning(true);
        mempoolBTCFee.setPeriodicity(5 * 60 * 1000L);
        taskRunner.addTask(mempoolBTCFee);
        providers.put(MempoolBTCFee.class.getSimpleName(), mempoolBTCFee);

        updateStatus(ServiceStatus.RUNNING);
        LOG.info("Started.");
        return true;
    }

    @Override
    public boolean shutdown() {
        LOG.info("Shutting down...");
        updateStatus(ServiceStatus.SHUTTING_DOWN);


        updateStatus(ServiceStatus.SHUTDOWN);
        LOG.info("Shutdown.");
        return true;
    }

    @Override
    public boolean gracefulShutdown() {
        LOG.info("Gracefully shutting down...");
        updateStatus(ServiceStatus.GRACEFULLY_SHUTTING_DOWN);


        updateStatus(ServiceStatus.GRACEFULLY_SHUTDOWN);
        LOG.info("Gracefully shutdown.");
        return true;
    }

    public static void main(String[] args) {
        WalletService service = new WalletService();
        Properties props = new Properties();
        for(String arg : args) {
            String[] nvp = arg.split("=");
            props.put(nvp[0],nvp[1]);
        }
        if(service.start(props)) {
            while(service.getServiceStatus() != ServiceStatus.SHUTDOWN) {
                try {
                    synchronized (service) {
                        service.wait(2 * 1000);
                    }
                } catch (InterruptedException e) {
                    System.exit(0);
                }
            }
        } else {
            System.exit(-1);
        }
    }

}

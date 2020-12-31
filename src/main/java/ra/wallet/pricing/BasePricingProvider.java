package ra.wallet.pricing;

import ra.util.tasks.BaseTask;
import ra.util.tasks.TaskRunner;

public abstract class BasePricingProvider extends BaseTask implements PricingProvider {

    public BasePricingProvider(String taskName, TaskRunner taskRunner) {
        super(taskName, taskRunner);
    }
}

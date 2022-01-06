package eu.mihosoft.concurrencyutils;

import eu.mihosoft.vmf.runtime.core.DelegatedBehaviorBase;

public class CounterBehavior extends DelegatedBehaviorBase<Counter> {

    public void inc() {

        // simulate long running task
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        getCaller().setValue(getCaller().getValue()+1);
    }

    public void dec() {

        // simulate long running task
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        getCaller().setValue(getCaller().getValue()-1);
    }

    public void reset() {
        getCaller().vmf().reflect().propertyByName("value").ifPresentOrElse(
            p->p.unset(), () -> {throw new RuntimeException("value property not found");}
        );
    }

}

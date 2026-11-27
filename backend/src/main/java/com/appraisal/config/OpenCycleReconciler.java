package com.appraisal.config;

import com.appraisal.service.CycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** On startup, repairs open cycles whose people drifted from the Users page (level, team, who takes part). */
@Component
public class OpenCycleReconciler {
    private static final Logger log = LoggerFactory.getLogger(OpenCycleReconciler.class);
    private final CycleService cycles;

    public OpenCycleReconciler(CycleService cycles) {
        this.cycles = cycles;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        int n = cycles.reconcileOpenCycles(null);
        if (n > 0) log.info("Brought {} appraisal(s) in open cycles in line with the Users page", n);
    }
}

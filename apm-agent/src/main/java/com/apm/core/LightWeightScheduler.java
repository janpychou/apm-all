package com.apm.core;

import com.apm.base.Scheduler;
import com.apm.base.util.ExecutorManager;
import com.apm.base.util.Logger;
import com.apm.base.util.ThreadUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class LightWeightScheduler {

    private static final ScheduledThreadPoolExecutor scheduledExecutor = new ScheduledThreadPoolExecutor(2,
            ThreadUtils.newThreadFactory("apm-LightWeightScheduler-"),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    private final List<Scheduler> schedulerList;

    private final long initialDelay;

    private final long period;

    private final TimeUnit unit;

    private final long millTimeSlice;

    private volatile long nextTimeSliceEndTime = 0L;

    public LightWeightScheduler(List<Scheduler> schedulerList,
                                long initialDelay,
                                long period,
                                TimeUnit unit,
                                long millTimeSlice) {
        this.schedulerList = Collections.unmodifiableList(schedulerList);
        this.millTimeSlice = millTimeSlice;
        this.initialDelay = initialDelay;
        this.period = period;
        this.unit = unit;
    }

    public static void initScheduleTask(List<Scheduler> schedulerList, long millTimeSlice) {
        ExecutorManager.addExecutorService(scheduledExecutor);

        new LightWeightScheduler(schedulerList, 0, 10, TimeUnit.MILLISECONDS, millTimeSlice).start();
    }


    private void start() {
        scheduledExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                long currentMills = System.currentTimeMillis();
                if (nextTimeSliceEndTime == 0L) {
                    nextTimeSliceEndTime = ((currentMills / millTimeSlice) * millTimeSlice) + millTimeSlice;
                }

                //还在当前的时间片里
                if (nextTimeSliceEndTime > currentMills) {
                    return;
                }
                nextTimeSliceEndTime = ((currentMills / millTimeSlice) * millTimeSlice) + millTimeSlice;
                runTasks(currentMills);
            }
        }, initialDelay, period, unit);
    }

    private void runTasks(long currentMills) {

        try {
            long lastTimeSliceStartTime = currentMills - millTimeSlice;
            for (int i = 0; i < schedulerList.size(); ++i) {
                runTask(schedulerList.get(i), lastTimeSliceStartTime);
            }
        } finally {
            Logger.debug("LightWeightScheduler.runTasks() cost: " + (System.currentTimeMillis() - currentMills) + "ms");
        }
    }

    private void runTask(Scheduler scheduler, long lastTimeSliceStartTime) {
        try {
            scheduler.run(lastTimeSliceStartTime, millTimeSlice);
        } catch (Exception e) {
            Logger.error("LightWeightScheduler.runTask(" + scheduler + ", " + lastTimeSliceStartTime + ")", e);
        }
    }

}
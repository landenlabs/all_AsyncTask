package com.landenlabs;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.lifecycle.Observer;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

import static com.landenlabs.WithRxJava.WorkResult.MSG;

// https://www.vogella.com/tutorials/RxJava/article.html
// https://stablekernel.com/article/replace-asynctask-and-asynctaskloader-with-rx-observable-rxjava-android-patterns/

public class WithRxJava
        extends androidx.fragment.app.Fragment
        implements View.OnClickListener {

    private TextView resultsTv;
    private NumPicker jobCounterNp;
    private CheckBox verboseCb;
    private StringBuilder sb = new StringBuilder();
    private SharedPreferences pref;

    private static final String PREF_MSG = "Msg";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        pref = requireContext().getSharedPreferences(getClass().getSimpleName(), Context.MODE_PRIVATE);
        sb.append(pref.getString(PREF_MSG, ""));
        return inflater.inflate(R.layout.fragment_with_rxjava, container, false);
    }

    @Override
    public void onPause() {
        pref.edit().putString(PREF_MSG, sb.toString()).apply();
        super.onPause();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        view.findViewById(R.id.testBtn1).setOnClickListener(this);
        view.findViewById(R.id.testBtn2).setOnClickListener(this);
        view.findViewById(R.id.testBtn3).setOnClickListener(this);
        view.findViewById(R.id.clearBtn).setOnClickListener(this);
        resultsTv = view.findViewById(R.id.textResult);

        if (sb.length() == 0) {
            printLn("Press tasks buttons to start a background job.");
            sb.delete(0, sb.length());
        } else {
            resultsTv.setText(sb);
        }

        verboseCb = view.findViewById(R.id.verboseCb);
        jobCounterNp = view.findViewById(R.id.jobCount);
    }

    void printLn(String msg) {
        sb.append(msg).append("\n");
        resultsTv.setText(sb);
    }

    void clear() {
        sb.delete(0, sb.length());
        resultsTv.setText("");
    }

    @Override
    public void onClick(View view) {
        JobSpec jobSpec = new JobSpec(verboseCb.isChecked(), jobCounterNp.getValue());

        int id = view.getId();
        switch (id) {
            case R.id.testBtn1:
                jobSpec.setPeriod(5).setName(" TEST . ");
                break;
            case R.id.testBtn2:
                jobSpec.setPeriod(8).setName( " TEST2 .. ");
                break;
            case R.id.testBtn3:
                jobSpec.setPeriod(10).setName( " TEST3 ... ");
                break;
            case R.id.clearBtn:
                clear();
                break;
        }
        example(jobSpec);
    }

    // ---------------------------------------------------------------------------------------------
    private void example(JobSpec runParams) {

        long statNano = System.nanoTime();
        // RunParams runParams = new RunParams(period, name);
        InitParams initParams = new InitParams();

        Disposable disposable =
        Observable.create(new ObservableOnSubscribe<WorkResult>() {
            // Similar to doInbackground of AsyncTask
            @Override public void subscribe(ObservableEmitter<WorkResult> emitter) throws Exception {
                if(!emitter.isDisposed()) {
                    WorkResult workResult;
                    // Do some work.
                    try {
                        long beginWorkNano = System.nanoTime();
                        workResult = new WorkResult(WorkResult.MSG,
                                Thread.currentThread().getId() + String.format(" deltaMilli=%,d", (beginWorkNano - statNano)/1000));
                        emitter.onNext(workResult);

                        JobSpec params = runParams;
                        if (params.verbose) {
                            workResult = new WorkResult(WorkResult.MSG,
                                    Thread.currentThread().getId() + params.name + " Begin sleeping " + params.period + " secs");
                            emitter.onNext(workResult);
                        }

                        // Example work to perform on background worker thread.
                        for (int idx = 0; idx < params.period; idx++) {
                            Thread.sleep(1000);
                            if (params.verbose) {
                                workResult = new WorkResult(WorkResult.MSG, Thread.currentThread().getId() + "  " + params.name + " sleeping " + idx);
                                emitter.onNext(workResult);
                            }
                        }
                        if (Math.random() < 0.3) {
                            throw new IllegalStateException("Random failure");
                        }

                        workResult = new WorkResult(WorkResult.DONE, Thread.currentThread().getId() + params.name + " Sleep completed");
                        emitter.onNext(workResult);
                    } catch (Exception ex) {
                        emitter.onError(ex);
                    }

                    emitter.onComplete();
                }
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(workResult -> {
                    if (workResult != null && workResult.action == MSG) {
                        printLn(workResult.data.toString());
                    } else {
                        JobSpec params = runParams;
                        long deltaMilli = (System.nanoTime() - statNano)/1000;
                        if (workResult != null) {
                            printLn( params.name + "[Task done] " + String.format("%,d", deltaMilli));
                        } else {
                            printLn( params.name + "[Task failed] " + String.format("%,d", deltaMilli));
                        }
                    }
                }, throwable -> {
                    // log(throwable);
                    printLn(" Task failed " + throwable.getMessage());
                });
    }

    static class InitParams {
        InitParams() {
        }
    }

    static class RunParams {
        int period;
        String msg;

        RunParams(int period, String msg) {
            this.period = period;
            this.msg = msg;
        }
    }

    static class WorkResult {
        public static final int MSG = 1;
        public static final int DONE = 2;
        public static final int ERROR = 3;

        public final int action;
        public final Object data;
        public WorkResult(int action, Object data) {
            this.action = action;
            this.data = data;
        }
    }

}

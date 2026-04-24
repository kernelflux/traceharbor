package com.kernelflux.traceharborsample;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;

/**
 * §5.3 FPS — long RecyclerView whose binder does intentional small main-thread work, so fast
 * scrolling generates dropped-frame samples for FrameTracer.
 */
public class FpsTestActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fps);

        RecyclerView recyclerView = findViewById(R.id.fps_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new HeavyAdapter(2_000));

        findViewById(R.id.fps_refresh_button).setOnClickListener(v ->
                IssueRecorder.appendEvent("fps", "FPS refresh tap"));
    }

    private static final class HeavyAdapter extends RecyclerView.Adapter<HeavyAdapter.VH> {
        private final int itemCount;

        HeavyAdapter(int itemCount) {
            this.itemCount = itemCount;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout root = new LinearLayout(parent.getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(32, 24, 32, 24);
            int height = (int) (parent.getResources().getDisplayMetrics().density * 96);
            root.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, height));

            TextView title = new TextView(parent.getContext());
            title.setId(android.R.id.text1);
            title.setTextSize(18f);
            root.addView(title);

            TextView body = new TextView(parent.getContext());
            body.setId(android.R.id.text2);
            body.setTextSize(13f);
            body.setTextColor(Color.DKGRAY);
            body.setGravity(Gravity.START);
            root.addView(body);

            return new VH(root);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            doSyntheticWork(8L);

            TextView title = holder.itemView.findViewById(android.R.id.text1);
            TextView body = holder.itemView.findViewById(android.R.id.text2);
            title.setText(String.format(Locale.US, "Item #%d", position));
            StringBuilder sb = new StringBuilder("payload");
            for (int i = 0; i < 16; i++) {
                sb.append(' ').append(Math.sqrt(position * 31.0 + i));
            }
            body.setText(sb);
        }

        @Override
        public int getItemCount() {
            return itemCount;
        }

        private static void doSyntheticWork(long ms) {
            long until = SystemClock.elapsedRealtime() + ms;
            double sink = 0.0;
            while (SystemClock.elapsedRealtime() < until) {
                sink += Math.tan(System.nanoTime() % 1_000_003);
            }
            if (sink == 42.0) {
                throw new AssertionError("unreachable");
            }
        }

        static final class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}

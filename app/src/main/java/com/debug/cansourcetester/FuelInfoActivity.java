package com.debug.cansourcetester;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Mirrors the fuel/mileage ("oil") and door/trunk display built by the real
 * CarInfo app (package com.hcn.autocan), specifically
 * com.hcn.autocan.hiworld.Honda.Yage9DataDecoder, reverse-engineered by
 * decompiling CarInfo.apk.
 *
 * Byte layout for fuel (ParseData case pData[1]==22, readCurrentOilInfo):
 *   pData[2]      instant consumption        (1 byte, 0-21 valid, else invalid)
 *   pData[3-4]    current average             (2 bytes)
 *   pData[5-6]    last-trip average           (2 bytes)
 *   pData[7-8]    overall average             (2 bytes)
 *   pData[9-11]   trip A distance             (3 bytes)
 *   pData[12-13]  total drive distance        (2 bytes)
 *   pData[14]     units bitfield              (bit7 dist unit, bit6 tripA unit,
 *                                               4-5/2-3/0-1 = per-field mpg/km per L/L per 100km)
 *   pData[15]     gauge range (progress max)
 *
 * History (pData[1]==23, readHistoryOilInfo): 3 slots, each 3-byte mileage +
 * 2-byte average, same units bitfield pattern at pData[17].
 *
 * Door/trunk (pData[1]==18, handleCarDetailInfo): 6 states packed into one
 * byte at pData[4] -- mFront=bit2 (hood), mRear=bit3 (trunk), FrontLeft=bit7,
 * FrontRight=bit6, RearLeft=bit5, RearRight=bit4.
 *
 * This screen shows SAMPLE data only. The real CarInfo app owns its own
 * dedicated serial connection to the decoder box (CanApp.GetInstance()
 * .getCanService().openSerialPort()) separate from McuManager -- opening
 * that same port from here risks conflicting with it if both run at once,
 * so no live read is wired up.
 */
public class FuelInfoActivity extends Activity {

    private static final int GAUGE_MAX = 60;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fuel_info);

        bindStat(R.id.cardInstant, "Instant", "8.4", "mpg", 8);
        bindStat(R.id.cardCurAvg, "Current avg (this trip)", "31.2", "mpg", 31);
        bindStat(R.id.cardLastAvg, "Last trip avg", "29.7", "mpg", 30);
        bindStat(R.id.cardOverallAvg, "Overall avg (lifetime)", "33.5", "mpg", 34);

        LinearLayout historyContainer = findViewById(R.id.historyContainer);
        addHistoryRow(historyContainer, "Reset 1", "412.6 km", "32.1 mpg");
        addHistoryRow(historyContainer, "Reset 2", "388.9 km", "30.4 mpg");
        addHistoryRow(historyContainer, "Reset 3", "501.2 km", "33.8 mpg");

        LinearLayout doorRow1 = findViewById(R.id.doorGridRow1);
        addDoorPill(doorRow1, "Hood", false);
        addDoorPill(doorRow1, "Trunk", false);
        addDoorPill(doorRow1, "Front L", true);

        LinearLayout doorRow2 = findViewById(R.id.doorGridRow2);
        addDoorPill(doorRow2, "Front R", false);
        addDoorPill(doorRow2, "Rear L", false);
        addDoorPill(doorRow2, "Rear R", false);
    }

    private void bindStat(int includeId, String label, String value, String unit, int progress) {
        View card = findViewById(includeId);
        ((TextView) card.findViewById(R.id.txtLabel)).setText(label);
        ((TextView) card.findViewById(R.id.txtValue)).setText(value + " " + unit);
        ProgressBar bar = card.findViewById(R.id.progressBar);
        bar.setMax(GAUGE_MAX);
        bar.setProgress(progress);
    }

    private void addHistoryRow(LinearLayout container, String label, String mileage, String avg) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(8);
        row.setLayoutParams(rowParams);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));

        TextView txtLabel = new TextView(this);
        txtLabel.setText(label);
        txtLabel.setTextColor(Color.parseColor("#AAAAAA"));
        txtLabel.setTextSize(13);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(txtLabel, labelParams);

        TextView txtMileage = new TextView(this);
        txtMileage.setText(mileage);
        txtMileage.setTextColor(Color.WHITE);
        txtMileage.setTextSize(13);
        txtMileage.setPadding(0, 0, dp(16), 0);
        row.addView(txtMileage);

        TextView txtAvg = new TextView(this);
        txtAvg.setText(avg);
        txtAvg.setTextColor(Color.parseColor("#4FC3F7"));
        txtAvg.setTextSize(13);
        txtAvg.setTypeface(txtAvg.getTypeface(), android.graphics.Typeface.BOLD);
        row.addView(txtAvg);

        container.addView(row);
    }

    private void addDoorPill(LinearLayout container, String label, boolean open) {
        TextView pill = new TextView(this);
        pill.setText((open ? "● " : "○ ") + label);
        pill.setTextColor(open ? Color.parseColor("#E74C3C") : Color.parseColor("#2ECC71"));
        pill.setTextSize(12);
        pill.setGravity(Gravity.CENTER);
        pill.setBackgroundResource(open ? R.drawable.bg_pill_open : R.drawable.bg_pill_closed);
        pill.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.marginEnd = dp(6);
        params.marginStart = dp(6);
        pill.setLayoutParams(params);
        container.addView(pill);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}

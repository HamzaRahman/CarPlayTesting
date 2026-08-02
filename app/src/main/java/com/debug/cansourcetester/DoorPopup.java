package com.debug.cansourcetester;

import android.app.Dialog;
import android.content.Context;
import android.view.Window;
import android.widget.TextView;

/**
 * Custom-styled top-down car diagram for door/trunk state, shown by our own
 * app -- separate from and non-interfering with the stock CarInfo.apk popup.
 * State layout matches handleCarDetailInfo() in the decompiled
 * Yage9DataDecoder: pData[4] bit2=hood, bit3=trunk, bit7=FL, bit6=FR,
 * bit5=RL, bit4=RR.
 */
public class DoorPopup {

    public static class State {
        public boolean hood;
        public boolean trunk;
        public boolean frontLeft;
        public boolean frontRight;
        public boolean rearLeft;
        public boolean rearRight;

        public boolean anyOpen() {
            return hood || trunk || frontLeft || frontRight || rearLeft || rearRight;
        }
    }

    public static void show(Context context, State state) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_door_popup);

        setZone(dialog, R.id.zoneHood, state.hood);
        setZone(dialog, R.id.zoneTrunk, state.trunk);
        setZone(dialog, R.id.zoneFrontLeft, state.frontLeft);
        setZone(dialog, R.id.zoneFrontRight, state.frontRight);
        setZone(dialog, R.id.zoneRearLeft, state.rearLeft);
        setZone(dialog, R.id.zoneRearRight, state.rearRight);

        TextView title = dialog.findViewById(R.id.txtPopupTitle);
        TextView summary = dialog.findViewById(R.id.txtPopupSummary);
        if (state.anyOpen()) {
            title.setText("⚠ Door / Trunk Open");
            summary.setText(buildOpenSummary(state));
        } else {
            title.setText("Door / Trunk Status");
            summary.setText("All closed");
        }

        dialog.findViewById(R.id.btnPopupClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static void setZone(Dialog dialog, int id, boolean open) {
        TextView zone = dialog.findViewById(id);
        zone.setBackgroundResource(open ? R.drawable.bg_zone_open : R.drawable.bg_zone_closed);
        zone.setTextColor(open ? 0xFFFF8A80 : 0xFFCFCFCF);
    }

    private static String buildOpenSummary(State state) {
        StringBuilder sb = new StringBuilder();
        if (state.hood) append(sb, "Hood");
        if (state.trunk) append(sb, "Trunk");
        if (state.frontLeft) append(sb, "Front Left door");
        if (state.frontRight) append(sb, "Front Right door");
        if (state.rearLeft) append(sb, "Rear Left door");
        if (state.rearRight) append(sb, "Rear Right door");
        sb.append(" open");
        return sb.toString();
    }

    private static void append(StringBuilder sb, String label) {
        if (sb.length() > 0) sb.append(", ");
        sb.append(label);
    }
}

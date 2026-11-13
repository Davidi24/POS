package pos.pos.tables.specialcase;

import pos.pos.settings.entity.Settings;

import java.time.OffsetDateTime;

// How long a table with guests can go without anyone doing anything, by what's on it. 0 = never.
public final class SpecialCaseLimits {

    private SpecialCaseLimits() {
    }

    public static int minutes(Settings settings, boolean hasUnpaidOrder, boolean paid) {
        if (paid) {
            return settings.getSpecialCasePaidMinutes();
        }
        return hasUnpaidOrder ? settings.getSpecialCaseOrderMinutes() : settings.getSpecialCaseSeatedMinutes();
    }

    public static OffsetDateTime latest(OffsetDateTime... times) {
        OffsetDateTime latest = null;
        for (OffsetDateTime time : times) {
            if (time != null && (latest == null || time.isAfter(latest))) {
                latest = time;
            }
        }
        return latest;
    }
}

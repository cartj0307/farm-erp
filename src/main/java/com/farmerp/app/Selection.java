package com.farmerp.app;

// Home base farm and field, set only from the Fields tab.
// Other tabs follow it when it changes and otherwise keep their own pick.
public final class Selection {

    private long farmId;
    private long fieldId;
    private long version;
    private long farmVersion;

    public long farmId() {
        return farmId;
    }

    public long fieldId() {
        return fieldId;
    }

    // Goes up on any change; used by tabs that pick a farm and field
    public long version() {
        return version;
    }

    // Goes up only when the farm changes; used by farm-only tabs
    public long farmVersion() {
        return farmVersion;
    }

    public void selectFarm(long newFarmId) {
        if (newFarmId != farmId) {
            farmId = newFarmId;
            fieldId = 0;
            version++;
            farmVersion++;
        }
    }

    public void selectField(long newFarmId, long newFieldId) {
        if (newFarmId != farmId) {
            selectFarm(newFarmId);
        }
        if (newFieldId != fieldId) {
            fieldId = newFieldId;
            version++;
        }
    }

    // Called when a farm is deleted
    public void forgetFarm(long deletedFarmId) {
        if (deletedFarmId == farmId) {
            farmId = 0;
            fieldId = 0;
            version++;
            farmVersion++;
        }
    }

    // Called when a field is deleted
    public void forgetField(long deletedFieldId) {
        if (deletedFieldId == fieldId) {
            fieldId = 0;
            version++;
        }
    }
}

package com.farmerp.yields;

import com.farmerp.app.Database;
import com.farmerp.common.ValidationException;
import com.farmerp.common.Validator;
import com.farmerp.fields.Field;
import com.farmerp.fields.FieldDao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

public class YieldService {

    static final int MIN_YEAR = 1900;
    static final int MAX_YEAR = 2100;

    private final YieldDao yieldDao;
    private final FieldDao fieldDao;

    public YieldService(Database db) {
        this(new YieldDao(db), new FieldDao(db));
    }

    public YieldService(YieldDao yieldDao, FieldDao fieldDao) {
        this.yieldDao = yieldDao;
        this.fieldDao = fieldDao;
    }

    public List<YieldRecord> yieldsFor(long fieldId) {
        return yieldDao.findByField(fieldId);
    }

    // id 0 = new yield, otherwise update (FR-8)
    public YieldRecord saveYield(YieldRecord y) {
        requireField(y.fieldId());
        if (y.cropYear() < MIN_YEAR || y.cropYear() > MAX_YEAR) {
            throw new ValidationException("Crop year must be between " + MIN_YEAR + " and " + MAX_YEAR + ".");
        }
        String crop = Validator.requireText(y.crop(), "Crop");
        if (y.harvestDate() == null) {
            throw new ValidationException("Harvest date is required.");
        }
        if (y.unit() == null) {
            throw new ValidationException("Pick a unit.");
        }
        double total = Validator.requireNonNegative(y.totalYield(), "Total yield");
        if (y.priceCentsPerUnit() < 0) {
            throw new ValidationException("Price can't be negative.");
        }
        if (yieldDao.exists(y.fieldId(), y.cropYear(), crop, y.id())) {
            throw new ValidationException("This field already has a " + crop + " yield for " + y.cropYear() + ".");
        }
        // Keeps expected yield from mixing bushels and tons
        for (YieldRecord other : yieldDao.findByFieldAndCrop(y.fieldId(), crop)) {
            if (other.id() != y.id() && other.unit() != y.unit()) {
                throw new ValidationException(crop + " on this field is recorded in " + other.unit()
                        + ". Use " + other.unit() + " for every year.");
            }
        }

        YieldRecord clean = new YieldRecord(y.id(), y.fieldId(), y.cropYear(), crop, y.harvestDate(),
                total, y.unit(), y.priceCentsPerUnit());
        if (clean.id() == 0) {
            return yieldDao.insert(clean);
        }
        yieldDao.update(clean);
        return clean;
    }

    public void deleteYield(long yieldId) {
        yieldDao.delete(yieldId);
    }

    // Average yield per acre from crop years before forYear, empty when there's no history (FR-9)
    public Optional<ExpectedYield> expectedYieldPerAcre(long fieldId, String crop, int forYear) {
        Field field = requireField(fieldId);
        if (crop == null || crop.isBlank()) {
            return Optional.empty();
        }
        List<YieldRecord> prior = yieldDao.findByFieldAndCrop(fieldId, crop.trim()).stream()
                .filter(y -> y.cropYear() < forYear)
                .toList();
        if (prior.isEmpty()) {
            return Optional.empty();
        }
        double sum = 0;
        for (YieldRecord y : prior) {
            sum += y.totalYield() / field.acres();
        }
        return Optional.of(new ExpectedYield(sum / prior.size(), prior.size(), prior.get(0).unit()));
    }

    // Total yield times price, rounded half up to the cent
    public static long revenueCents(double totalYield, long priceCentsPerUnit) {
        return BigDecimal.valueOf(totalYield)
                .multiply(BigDecimal.valueOf(priceCentsPerUnit))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private Field requireField(long fieldId) {
        return fieldDao.findById(fieldId)
                .orElseThrow(() -> new ValidationException("Pick a field first."));
    }
}

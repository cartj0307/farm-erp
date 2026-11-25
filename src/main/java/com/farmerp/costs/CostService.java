package com.farmerp.costs;

import com.farmerp.app.Database;
import com.farmerp.common.ValidationException;
import com.farmerp.common.Validator;
import com.farmerp.fields.FieldDao;

import java.util.List;

public class CostService {

    static final int MIN_YEAR = 1900;
    static final int MAX_YEAR = 2100;

    private final InputCostDao costDao;
    private final FieldDao fieldDao;

    public CostService(Database db) {
        this(new InputCostDao(db), new FieldDao(db));
    }

    public CostService(InputCostDao costDao, FieldDao fieldDao) {
        this.costDao = costDao;
        this.fieldDao = fieldDao;
    }

    public List<InputCost> costsFor(long fieldId, int cropYear) {
        return costDao.findByField(fieldId, cropYear);
    }

    public long totalFor(long fieldId, int cropYear) {
        return costDao.totalForField(fieldId, cropYear);
    }

    // id 0 = new cost, otherwise update (UC-5)
    public InputCost saveCost(InputCost cost) {
        if (fieldDao.findById(cost.fieldId()).isEmpty()) {
            throw new ValidationException("Pick a field first.");
        }
        if (cost.cropYear() < MIN_YEAR || cost.cropYear() > MAX_YEAR) {
            throw new ValidationException("Crop year must be between " + MIN_YEAR + " and " + MAX_YEAR + ".");
        }
        if (cost.date() == null) {
            throw new ValidationException("Date is required.");
        }
        if (cost.category() == null) {
            throw new ValidationException("Pick a category.");
        }
        long amount = Validator.requirePositive(cost.amountCents(), "Amount");
        String description = cost.description() == null ? "" : cost.description().trim();

        InputCost clean = new InputCost(cost.id(), cost.fieldId(), cost.cropYear(), cost.date(),
                cost.category(), description, amount);
        if (clean.id() == 0) {
            return costDao.insert(clean);
        }
        costDao.update(clean);
        return clean;
    }

    public void deleteCost(long costId) {
        costDao.delete(costId);
    }
}

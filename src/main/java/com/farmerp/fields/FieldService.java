package com.farmerp.fields;

import com.farmerp.app.Database;
import com.farmerp.common.ValidationException;
import com.farmerp.common.Validator;

import java.util.List;

public class FieldService {

    private final FarmDao farmDao;
    private final FieldDao fieldDao;

    public FieldService(Database db) {
        this(new FarmDao(db), new FieldDao(db));
    }

    public FieldService(FarmDao farmDao, FieldDao fieldDao) {
        this.farmDao = farmDao;
        this.fieldDao = fieldDao;
    }

    public List<Farm> listFarms() {
        return farmDao.findAll();
    }

    // id 0 = new farm, otherwise update
    public Farm saveFarm(Farm farm) {
        String name = Validator.requireText(farm.name(), "Farm name");
        double acres = Validator.requirePositive(farm.totalAcres(), "Total acres");
        String location = farm.location() == null ? "" : farm.location().trim();
        Farm clean = new Farm(farm.id(), name, location, acres);

        if (clean.id() == 0) {
            return farmDao.insert(clean);
        }
        farmDao.update(clean);
        return clean;
    }

    public void deleteFarm(long farmId) {
        farmDao.delete(farmId);
    }

    public List<Field> listFields(long farmId) {
        return fieldDao.findByFarm(farmId);
    }

    // id 0 = new field, otherwise update
    public Field saveField(Field field) {
        if (farmDao.findById(field.farmId()).isEmpty()) {
            throw new ValidationException("Pick a farm before adding a field.");
        }
        String name = Validator.requireText(field.name(), "Field name");
        double acres = Validator.requirePositive(field.acres(), "Acres");
        String notes = field.notes() == null ? "" : field.notes().trim();

        if (fieldDao.nameExists(field.farmId(), name, field.id())) {
            throw new ValidationException("This farm already has a field named " + name + ".");
        }

        Field clean = new Field(field.id(), field.farmId(), name, acres, notes);
        if (clean.id() == 0) {
            return fieldDao.insert(clean);
        }
        fieldDao.update(clean);
        return clean;
    }

    public void deleteField(long fieldId) {
        fieldDao.delete(fieldId);
    }
}

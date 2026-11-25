package com.farmerp.records;

import com.farmerp.app.Database;
import com.farmerp.common.ValidationException;
import com.farmerp.common.Validator;
import com.farmerp.fields.FieldDao;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RecordService {

    private static final DecimalFormat NUM = new DecimalFormat("#,##0.##");

    private final SoilSampleDao sampleDao;
    private final ApplicationDao applicationDao;
    private final FieldDao fieldDao;

    public RecordService(Database db) {
        this(new SoilSampleDao(db), new ApplicationDao(db), new FieldDao(db));
    }

    public RecordService(SoilSampleDao sampleDao, ApplicationDao applicationDao, FieldDao fieldDao) {
        this.sampleDao = sampleDao;
        this.applicationDao = applicationDao;
        this.fieldDao = fieldDao;
    }

    // ---------- soil samples ----------

    public List<SoilSample> listSoilSamples(long fieldId) {
        return sampleDao.findByField(fieldId);
    }

    // id 0 = new sample, otherwise update
    public SoilSample saveSoilSample(SoilSample s) {
        requireField(s.fieldId());
        if (s.sampleDate() == null) {
            throw new ValidationException("Sample date is required.");
        }
        if (s.nitrogenPpm() == null && s.phosphorusPpm() == null && s.potassiumPpm() == null
                && s.ph() == null && s.organicMatterPct() == null) {
            throw new ValidationException("Enter at least one soil test value.");
        }
        if (s.nitrogenPpm() != null) {
            Validator.requireNonNegative(s.nitrogenPpm(), "Nitrogen");
        }
        if (s.phosphorusPpm() != null) {
            Validator.requireNonNegative(s.phosphorusPpm(), "Phosphorus");
        }
        if (s.potassiumPpm() != null) {
            Validator.requireNonNegative(s.potassiumPpm(), "Potassium");
        }
        if (s.ph() != null) {
            Validator.requireRange(s.ph(), 0, 14, "pH");
        }
        if (s.organicMatterPct() != null) {
            Validator.requireRange(s.organicMatterPct(), 0, 100, "Organic matter");
        }

        if (s.id() == 0) {
            return sampleDao.insert(s);
        }
        sampleDao.update(s);
        return s;
    }

    public void deleteSoilSample(long sampleId) {
        sampleDao.delete(sampleId);
    }

    // ---------- applications ----------

    public List<Application> listApplications(long fieldId) {
        return applicationDao.findByField(fieldId);
    }

    // id 0 = new application, otherwise update
    public Application saveApplication(Application a) {
        requireField(a.fieldId());
        if (a.date() == null) {
            throw new ValidationException("Application date is required.");
        }
        if (a.type() == null) {
            throw new ValidationException("Pick an application type.");
        }
        double amount = Validator.requirePositive(a.amount(), "Amount");
        String unit = Validator.requireText(a.unit(), "Unit");
        String product = a.product() == null ? "" : a.product().trim();

        Application clean = new Application(a.id(), a.fieldId(), a.date(), a.type(), product, amount, unit);
        if (clean.id() == 0) {
            return applicationDao.insert(clean);
        }
        applicationDao.update(clean);
        return clean;
    }

    public void deleteApplication(long applicationId) {
        applicationDao.delete(applicationId);
    }

    // ---------- history (FR-5) ----------

    // Samples and applications together, newest first
    public List<HistoryEntry> fieldHistory(long fieldId) {
        List<HistoryEntry> history = new ArrayList<>();
        for (SoilSample s : sampleDao.findByField(fieldId)) {
            history.add(new HistoryEntry(s.sampleDate(), "Soil sample", describe(s)));
        }
        for (Application a : applicationDao.findByField(fieldId)) {
            history.add(new HistoryEntry(a.date(), a.type() + " application", describe(a)));
        }
        history.sort(Comparator.comparing(HistoryEntry::date).reversed());
        return history;
    }

    static String describe(SoilSample s) {
        List<String> parts = new ArrayList<>();
        if (s.nitrogenPpm() != null) {
            parts.add("N " + NUM.format(s.nitrogenPpm()) + " ppm");
        }
        if (s.phosphorusPpm() != null) {
            parts.add("P " + NUM.format(s.phosphorusPpm()) + " ppm");
        }
        if (s.potassiumPpm() != null) {
            parts.add("K " + NUM.format(s.potassiumPpm()) + " ppm");
        }
        if (s.ph() != null) {
            parts.add("pH " + NUM.format(s.ph()));
        }
        if (s.organicMatterPct() != null) {
            parts.add("OM " + NUM.format(s.organicMatterPct()) + "%");
        }
        return String.join(", ", parts);
    }

    static String describe(Application a) {
        String amount = NUM.format(a.amount()) + " " + a.unit();
        return a.product().isEmpty() ? amount : a.product() + ", " + amount;
    }

    private void requireField(long fieldId) {
        if (fieldDao.findById(fieldId).isEmpty()) {
            throw new ValidationException("Pick a field first.");
        }
    }
}

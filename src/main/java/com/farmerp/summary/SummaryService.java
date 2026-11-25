package com.farmerp.summary;

import com.farmerp.app.Database;
import com.farmerp.common.Money;
import com.farmerp.common.ValidationException;
import com.farmerp.costs.CostCategory;
import com.farmerp.costs.CostTotal;
import com.farmerp.costs.InputCostDao;
import com.farmerp.fields.Farm;
import com.farmerp.fields.FarmDao;
import com.farmerp.fields.Field;
import com.farmerp.fields.FieldDao;
import com.farmerp.yields.YieldDao;
import com.farmerp.yields.YieldRecord;
import com.farmerp.yields.YieldService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Read only: builds the farm summary for UC-11 (FR-14, FR-15, FR-16)
public class SummaryService {

    private final FarmDao farmDao;
    private final FieldDao fieldDao;
    private final InputCostDao costDao;
    private final YieldDao yieldDao;

    public SummaryService(Database db) {
        this(new FarmDao(db), new FieldDao(db), new InputCostDao(db), new YieldDao(db));
    }

    public SummaryService(FarmDao farmDao, FieldDao fieldDao, InputCostDao costDao, YieldDao yieldDao) {
        this.farmDao = farmDao;
        this.fieldDao = fieldDao;
        this.costDao = costDao;
        this.yieldDao = yieldDao;
    }

    public FarmSummary farmSummary(long farmId, int cropYear) {
        Farm farm = farmDao.findById(farmId)
                .orElseThrow(() -> new ValidationException("Pick a farm first."));

        // Three queries total, no matter how many fields
        List<Field> fields = fieldDao.findByFarm(farmId);
        Map<Long, Map<CostCategory, Long>> costs = new HashMap<>();
        for (CostTotal t : costDao.totalsByField(farmId, cropYear)) {
            costs.computeIfAbsent(t.fieldId(), k -> new EnumMap<>(CostCategory.class)).put(t.category(), t.cents());
        }
        Map<Long, List<YieldRecord>> yields = new HashMap<>();
        for (YieldRecord y : yieldDao.findByFarmYear(farmId, cropYear)) {
            yields.computeIfAbsent(y.fieldId(), k -> new ArrayList<>()).add(y);
        }

        List<FieldSummary> rows = new ArrayList<>();
        Map<CostCategory, Long> farmByCategory = new EnumMap<>(CostCategory.class);
        double totalAcres = 0;
        long totalCost = 0;
        long totalRevenue = 0;
        boolean anyYield = false;

        for (Field field : fields) {
            Map<CostCategory, Long> byCategory = costs.getOrDefault(field.id(), new EnumMap<>(CostCategory.class));
            long cost = byCategory.values().stream().mapToLong(Long::longValue).sum();
            byCategory.forEach((cat, cents) -> farmByCategory.merge(cat, cents, Long::sum));

            List<YieldRecord> fieldYields = yields.getOrDefault(field.id(), List.of());
            Long revenue = null;
            Long net = null;
            Long netPerAcre = null;
            Set<String> crops = new LinkedHashSet<>();
            if (!fieldYields.isEmpty()) {
                long r = 0;
                for (YieldRecord y : fieldYields) {
                    r += YieldService.revenueCents(y.totalYield(), y.priceCentsPerUnit());
                    crops.add(y.crop());
                }
                revenue = r;
                net = r - cost;
                netPerAcre = netPerAcre(net, field.acres());
                totalRevenue += r;
                anyYield = true;
            }

            rows.add(new FieldSummary(field.id(), field.name(), field.acres(), String.join(", ", crops),
                    Collections.unmodifiableMap(byCategory), cost, costPerAcre(cost, field.acres()),
                    revenue, net, netPerAcre));
            totalAcres += field.acres();
            totalCost += cost;
        }

        Long farmRevenue = anyYield ? totalRevenue : null;
        Long farmNet = anyYield ? totalRevenue - totalCost : null;
        Long farmNetPerAcre = anyYield ? netPerAcre(farmNet, totalAcres) : null;
        return new FarmSummary(farm.id(), farm.name(), cropYear, List.copyOf(rows),
                Collections.unmodifiableMap(farmByCategory), totalAcres, totalCost,
                costPerAcre(totalCost, totalAcres), farmRevenue, farmNet, farmNetPerAcre);
    }

    // C acre = C total / A, rounded half up to the cent; 0 when there are no acres
    static long costPerAcre(long cents, double acres) {
        return acres <= 0 ? 0 : Money.divide(cents, acres);
    }

    // N acre = ((Y x p) - C total) / A
    static Long netPerAcre(long netCents, double acres) {
        return acres <= 0 ? null : Money.divide(netCents, acres);
    }
}

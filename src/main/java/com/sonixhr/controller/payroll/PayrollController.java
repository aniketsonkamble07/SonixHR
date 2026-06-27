package com.sonixhr.controller.payroll;

import com.sonixhr.dto.payroll.PayrollCalculationRequest;
import com.sonixhr.dto.payroll.PayrollCalculationResponse;
import com.sonixhr.entity.payroll.StateProfessionalTaxConfig;
import com.sonixhr.entity.payroll.StatutoryRateConfig;
import com.sonixhr.enums.IndianState;
import com.sonixhr.repository.payroll.StateProfessionalTaxConfigRepository;
import com.sonixhr.repository.payroll.StatutoryRateConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/public/payroll")
@CrossOrigin(origins = "*")
public class PayrollController {

    @Autowired
    private StatutoryRateConfigRepository statutoryRateConfigRepo;

    @Autowired
    private StateProfessionalTaxConfigRepository statePtConfigRepo;

    @PostMapping("/calculate")
    public PayrollCalculationResponse calculate(@RequestBody PayrollCalculationRequest req) {
        BigDecimal ctc = req.getCtc() != null ? req.getCtc() : BigDecimal.ZERO;
        IndianState state = req.getState() != null ? req.getState() : IndianState.MH;
        int month = req.getMonth();
        int year = req.getYear() > 0 ? req.getYear() : 2025;
        BigDecimal lopDays = req.getLopDays() != null ? req.getLopDays() : BigDecimal.ZERO;
        boolean compliantMode = req.isCompliantMode();
        boolean pfCapping = req.isPfCapping();
        BigDecimal esiPeriodStartGross = req.getEsiPeriodStartGross() != null ? req.getEsiPeriodStartGross() : BigDecimal.ZERO;

        LocalDate date = LocalDate.of(year, month, 1);

        // Fetch active statutory configs or use default fallback rates
        List<StatutoryRateConfig> activeRates = statutoryRateConfigRepo.findActiveByDate(date);
        double epfErRate = getRate(activeRates, "EPF_ER", 0.12);
        double epsErRate = getRate(activeRates, "EPS_ER", 0.0833);
        double epsErCap = getCap(activeRates, "EPS_ER", 1250.0);
        double edliRate = getRate(activeRates, "EDLI", 0.005);
        double edliCeiling = getCeiling(activeRates, "EDLI", 15000.0);
        double esiErRate = getRate(activeRates, "ESI_ER", 0.0325);
        double esiEeRate = getRate(activeRates, "ESI_EE", 0.0075);

        // Step 1: Base allowances split (Pass 1)
        BigDecimal basicBase = ctc.multiply(BigDecimal.valueOf(0.50)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal hraBase = basicBase.multiply(BigDecimal.valueOf(0.40)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal wagesBaseBase = basicBase;
        if (compliantMode) {
            wagesBaseBase = basicBase.max(ctc.multiply(BigDecimal.valueOf(0.50)).setScale(2, RoundingMode.HALF_UP));
        }

        // Compute base employer contributions
        BigDecimal epsErBaseVal = wagesBaseBase.multiply(BigDecimal.valueOf(epsErRate));
        BigDecimal epsErBase = BigDecimal.valueOf(Math.min(Math.round(epsErBaseVal.doubleValue()), epsErCap)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal epfErBase = wagesBaseBase.multiply(BigDecimal.valueOf(epfErRate)).setScale(2, RoundingMode.HALF_UP).subtract(epsErBase);
        if (epfErBase.compareTo(BigDecimal.ZERO) < 0) {
            epfErBase = BigDecimal.ZERO;
        }
        BigDecimal edliBase = wagesBaseBase.min(BigDecimal.valueOf(edliCeiling)).multiply(BigDecimal.valueOf(edliRate)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal esiErBase = BigDecimal.ZERO;
        if (esiPeriodStartGross.compareTo(BigDecimal.valueOf(21000)) <= 0) {
            esiErBase = wagesBaseBase.multiply(BigDecimal.valueOf(esiErRate)).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal employerContributionsBase = epfErBase.add(epsErBase).add(edliBase).add(esiErBase);
        BigDecimal specialAllowanceBase = ctc.subtract(basicBase).subtract(hraBase).subtract(employerContributionsBase);
        if (specialAllowanceBase.compareTo(BigDecimal.ZERO) < 0) {
            specialAllowanceBase = BigDecimal.ZERO;
        }

        // Step 2: Apply Proration and LOP (Pass 2)
        int totalDays = 30; // standard mock month
        BigDecimal prorationFactor = BigDecimal.ONE;

        BigDecimal basic = basicBase.multiply(prorationFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal basicLop = basic.divide(BigDecimal.valueOf(totalDays), 6, RoundingMode.HALF_UP).multiply(lopDays).setScale(2, RoundingMode.HALF_UP);
        basic = basic.subtract(basicLop).max(BigDecimal.ZERO);

        BigDecimal hra = hraBase.multiply(prorationFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal hraLop = hra.divide(BigDecimal.valueOf(totalDays), 6, RoundingMode.HALF_UP).multiply(lopDays).setScale(2, RoundingMode.HALF_UP);
        hra = hra.subtract(hraLop).max(BigDecimal.ZERO);

        BigDecimal specialAllowance = specialAllowanceBase.multiply(prorationFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal specialAllowanceLop = specialAllowance.divide(BigDecimal.valueOf(totalDays), 6, RoundingMode.HALF_UP).multiply(lopDays).setScale(2, RoundingMode.HALF_UP);
        specialAllowance = specialAllowance.subtract(specialAllowanceLop).max(BigDecimal.ZERO);

        BigDecimal grossEarnings = basic.add(hra).add(specialAllowance);

        // Recalculate active statutory calculations
        BigDecimal activeCtc = ctc.multiply(prorationFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal wagesBase = basic;
        if (compliantMode) {
            wagesBase = basic.max(activeCtc.multiply(BigDecimal.valueOf(0.50)).setScale(2, RoundingMode.HALF_UP));
        }

        // Deductions
        BigDecimal epfEe = BigDecimal.ZERO;
        if (pfCapping) {
            epfEe = wagesBase.multiply(BigDecimal.valueOf(0.12)).setScale(2, RoundingMode.HALF_UP).min(BigDecimal.valueOf(1800.00));
        } else {
            epfEe = wagesBase.multiply(BigDecimal.valueOf(0.12)).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal esiEe = BigDecimal.ZERO;
        BigDecimal esiEr = BigDecimal.ZERO;
        if (esiPeriodStartGross.compareTo(BigDecimal.valueOf(21000)) <= 0) {
            esiEe = wagesBase.multiply(BigDecimal.valueOf(esiEeRate)).setScale(2, RoundingMode.HALF_UP);
            esiEr = wagesBase.multiply(BigDecimal.valueOf(esiErRate)).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal epsErVal = wagesBase.multiply(BigDecimal.valueOf(epsErRate));
        BigDecimal epsEr = BigDecimal.valueOf(Math.min(Math.round(epsErVal.doubleValue()), epsErCap)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal epfEr = wagesBase.multiply(BigDecimal.valueOf(epfErRate)).setScale(2, RoundingMode.HALF_UP).subtract(epsEr);
        if (epfEr.compareTo(BigDecimal.ZERO) < 0) {
            epfEr = BigDecimal.ZERO;
        }
        BigDecimal edli = wagesBase.min(BigDecimal.valueOf(edliCeiling)).multiply(BigDecimal.valueOf(edliRate)).setScale(2, RoundingMode.HALF_UP);

        // Professional Tax Slab Evaluation
        List<StateProfessionalTaxConfig> slabs = statePtConfigRepo.findAll();
        BigDecimal pt = calculatePT(state, grossEarnings, month, slabs);

        BigDecimal totalDeductions = epfEe.add(esiEe).add(pt);
        BigDecimal netPay = grossEarnings.subtract(totalDeductions).max(BigDecimal.ZERO);
        BigDecimal totalEmployerContributions = epfEr.add(epsEr).add(edli).add(esiEr);
        BigDecimal reconciledCtc = grossEarnings.add(totalEmployerContributions);

        Map<String, BigDecimal> components = new LinkedHashMap<>();
        components.put("BASIC", basic);
        components.put("HRA", hra);
        components.put("SPECIAL_ALLOWANCE", specialAllowance);
        components.put("EPF_EE", epfEe);
        components.put("ESI_EE", esiEe);
        components.put("PT", pt);
        components.put("EPF_ER", epfEr);
        components.put("EPS_ER", epsEr);
        components.put("EDLI", edli);
        components.put("ESI_ER", esiEr);

        return PayrollCalculationResponse.builder()
                .ctc(ctc)
                .grossEarnings(grossEarnings)
                .totalDeductions(totalDeductions)
                .netPay(netPay)
                .wagesBase(wagesBase)
                .totalEmployerContributions(totalEmployerContributions)
                .reconciledCtc(reconciledCtc)
                .components(components)
                .build();
    }

    private double getRate(List<StatutoryRateConfig> rates, String code, double defaultVal) {
        return rates.stream()
                .filter(r -> r.getComponentCode().equalsIgnoreCase(code))
                .map(r -> r.getRate().doubleValue())
                .findFirst()
                .orElse(defaultVal);
    }

    private double getCap(List<StatutoryRateConfig> rates, String code, double defaultVal) {
        return rates.stream()
                .filter(r -> r.getComponentCode().equalsIgnoreCase(code))
                .filter(r -> r.getCapAmount() != null)
                .map(r -> r.getCapAmount().doubleValue())
                .findFirst()
                .orElse(defaultVal);
    }

    private double getCeiling(List<StatutoryRateConfig> rates, String code, double defaultVal) {
        return rates.stream()
                .filter(r -> r.getComponentCode().equalsIgnoreCase(code))
                .filter(r -> r.getCeilingAmount() != null)
                .map(r -> r.getCeilingAmount().doubleValue())
                .findFirst()
                .orElse(defaultVal);
    }

    private BigDecimal calculatePT(IndianState state, BigDecimal gross, int month, List<StateProfessionalTaxConfig> slabs) {
        if (slabs == null || slabs.isEmpty()) {
            if (state == IndianState.MH) {
                if (gross.compareTo(BigDecimal.valueOf(7500)) <= 0) return BigDecimal.ZERO;
                if (gross.compareTo(BigDecimal.valueOf(10000)) <= 0) return BigDecimal.valueOf(175.00);
                return BigDecimal.valueOf(month == 2 ? 300.00 : 200.00);
            } else if (state == IndianState.KA) {
                if (gross.compareTo(BigDecimal.valueOf(25000)) >= 0) return BigDecimal.valueOf(200.00);
                return BigDecimal.ZERO;
            }
            return BigDecimal.ZERO;
        }

        for (StateProfessionalTaxConfig slab : slabs) {
            if (slab.getStateCode() == state) {
                if (slab.getApplicableMonth() != null && slab.getApplicableMonth() != month) {
                    continue;
                }
                boolean matchesMin = (slab.getSalaryRangeMin().compareTo(BigDecimal.ZERO) == 0)
                        ? (gross.compareTo(slab.getSalaryRangeMin()) >= 0)
                        : (gross.compareTo(slab.getSalaryRangeMin()) > 0);

                boolean matchesMax = (slab.getSalaryRangeMax() == null)
                        || (gross.compareTo(slab.getSalaryRangeMax()) <= 0);

                if (matchesMin && matchesMax) {
                    return slab.getAmount();
                }
            }
        }
        return BigDecimal.ZERO;
    }
}

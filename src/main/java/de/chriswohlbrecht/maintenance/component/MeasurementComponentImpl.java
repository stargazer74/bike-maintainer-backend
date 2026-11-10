package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.BrakeMeasurementRequest;
import de.chriswohlbrecht.maintenance.api.model.BrakeMeasurementResponse;
import de.chriswohlbrecht.maintenance.api.model.ChainMeasurementRequest;
import de.chriswohlbrecht.maintenance.api.model.ChainMeasurementResponse;
import de.chriswohlbrecht.maintenance.api.model.LatestMeasurementsResponse;
import de.chriswohlbrecht.maintenance.api.model.LatestMeasurementsResponseSources;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceMeasurementsRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceMeasurementsResponse;
import de.chriswohlbrecht.maintenance.api.model.MeasurementSource;
import de.chriswohlbrecht.maintenance.api.model.ShimCalculationInput;
import de.chriswohlbrecht.maintenance.api.model.ShimCalculationRequest;
import de.chriswohlbrecht.maintenance.api.model.ShimCalculationResponse;
import de.chriswohlbrecht.maintenance.api.model.ShimCalculationResult;
import de.chriswohlbrecht.maintenance.api.model.TireMeasurementRequest;
import de.chriswohlbrecht.maintenance.api.model.TireMeasurementResponse;
import de.chriswohlbrecht.maintenance.api.model.ValveClearanceMeasurementRequest;
import de.chriswohlbrecht.maintenance.api.model.ValveClearanceMeasurementResponse;
import de.chriswohlbrecht.maintenance.api.model.ValveMeasurementRequest;
import de.chriswohlbrecht.maintenance.api.model.ValveMeasurementResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleSpecifications;
import de.chriswohlbrecht.maintenance.component.helper.MeasurementStatusHelper;
import de.chriswohlbrecht.maintenance.component.helper.ShimCalculator;
import de.chriswohlbrecht.maintenance.exception.InvalidMeasurementException;
import de.chriswohlbrecht.maintenance.mapper.MeasurementMapper;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLog;
import de.chriswohlbrecht.maintenance.persistence.model.MeasurementBrakes;
import de.chriswohlbrecht.maintenance.persistence.model.MeasurementChain;
import de.chriswohlbrecht.maintenance.persistence.model.MeasurementTires;
import de.chriswohlbrecht.maintenance.persistence.model.MeasurementValve;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.model.VehicleSpecification;
import de.chriswohlbrecht.maintenance.persistence.repository.MeasurementBrakesRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MeasurementChainRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MeasurementTiresRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MeasurementValveRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleSpecificationRepository;
import de.chriswohlbrecht.maintenance.persistence.type.ValveKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MeasurementComponentImpl implements IMeasurementComponent {

    private static final int MM_SCALE = 3;
    private static final int BAR_SCALE = 2;

    /** Latest maintenance first: by date, then by ID (order of entry). */
    private static final Comparator<MaintenanceLog> LATEST_FIRST = Comparator
            .comparing(MaintenanceLog::getPerformedAt)
            .thenComparing(MaintenanceLog::getId)
            .reversed();

    private static final Comparator<MeasurementValve> VALVE_ORDER = Comparator
            .comparingInt(MeasurementValve::getCylinder)
            .thenComparing(MeasurementValve::getValve)
            .thenComparingInt(MeasurementValve::getValveIndex);

    private final VehicleRepository vehicleRepository;
    private final VehicleSpecificationRepository vehicleSpecificationRepository;
    private final MeasurementValveRepository measurementValveRepository;
    private final MeasurementBrakesRepository measurementBrakesRepository;
    private final MeasurementChainRepository measurementChainRepository;
    private final MeasurementTiresRepository measurementTiresRepository;
    private final MeasurementMapper measurementMapper;
    private final ICurrentUserComponent currentUserComponent;

    @Override
    @Transactional(readOnly = true)
    public Optional<VehicleSpecifications> getVehicleSpecifications(Long vehicleId) {
        return findOwnVehicle(vehicleId).map(vehicle -> vehicleSpecificationRepository.findById(vehicleId)
                .map(measurementMapper::toApi)
                .orElseGet(VehicleSpecifications::new));
    }

    @Override
    @Transactional
    public Optional<VehicleSpecifications> updateVehicleSpecifications(Long vehicleId,
                                                                      VehicleSpecifications request) {
        return findOwnVehicle(vehicleId).map(vehicle -> {
            validate(request);
            VehicleSpecification specification = measurementMapper.toEntity(request);
            specification.setVehicleId(vehicleId);
            return measurementMapper.toApi(vehicleSpecificationRepository.save(specification));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LatestMeasurementsResponse> getLatestMeasurements(Long vehicleId) {
        return findOwnVehicle(vehicleId).map(vehicle -> {
            VehicleSpecification spec = specificationOf(vehicleId);
            MaintenanceMeasurementsResponse measurements = new MaintenanceMeasurementsResponse();
            LatestMeasurementsResponseSources sources = new LatestMeasurementsResponseSources();

            Map<MaintenanceLog, List<MeasurementValve>> valvesByLog = measurementValveRepository
                    .findAllByLog_Vehicle_Id(vehicleId).stream()
                    .collect(Collectors.groupingBy(MeasurementValve::getLog));
            valvesByLog.keySet().stream().min(LATEST_FIRST).ifPresent(log -> {
                measurements.setValveClearance(valveResponse(valvesByLog.get(log), spec));
                sources.setValveClearance(source(log));
            });
            latest(measurementBrakesRepository.findAllByLog_Vehicle_Id(vehicleId), MeasurementBrakes::getLog)
                    .ifPresent(brakes -> {
                        measurements.setBrakes(brakesResponse(brakes, spec));
                        sources.setBrakes(source(brakes.getLog()));
                    });
            latest(measurementChainRepository.findAllByLog_Vehicle_Id(vehicleId), MeasurementChain::getLog)
                    .ifPresent(chain -> {
                        measurements.setChain(chainResponse(chain, spec));
                        sources.setChain(source(chain.getLog()));
                    });
            latest(measurementTiresRepository.findAllByLog_Vehicle_Id(vehicleId), MeasurementTires::getLog)
                    .ifPresent(tires -> {
                        measurements.setTires(tiresResponse(tires, spec));
                        sources.setTires(source(tires.getLog()));
                    });
            return new LatestMeasurementsResponse(measurements, sources);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShimCalculationResponse> calculateShims(Long vehicleId, ShimCalculationRequest request) {
        return findOwnVehicle(vehicleId).map(vehicle -> {
            VehicleSpecification spec = specificationOf(vehicleId);
            List<ShimCalculationResult> results = new ArrayList<>();
            for (ShimCalculationInput input : request.getValves()) {
                requireScale(input.getMeasured(), MM_SCALE, "measured");
                requireScale(input.getShimOld(), MM_SCALE, "shimOld");
                ShimCalculator.Result result = ShimCalculator.calculate(ValveKind.valueOf(input.getValve().name()),
                        input.getMeasured(), input.getShimOld(), spec);
                results.add(new ShimCalculationResult()
                        .suggestedShim(result.suggestedShim())
                        .resultingClearance(result.resultingClearance())
                        .hint(result.hint()));
            }
            return new ShimCalculationResponse(results);
        });
    }

    @Override
    public void replaceMeasurements(MaintenanceLog log, MaintenanceMeasurementsRequest request) {
        if (request == null) {
            return;
        }
        List<MeasurementValve> valves = valvesOf(request.getValveClearance());
        MeasurementBrakes brakes = brakesOf(request.getBrakes());
        MeasurementChain chain = chainOf(request.getChain());
        MeasurementTires tires = tiresOf(request.getTires());

        measurementValveRepository.deleteAllByLog_Id(log.getId());
        measurementBrakesRepository.deleteAllByLog_Id(log.getId());
        measurementChainRepository.deleteAllByLog_Id(log.getId());
        measurementTiresRepository.deleteAllByLog_Id(log.getId());
        // Delete before insert: the unique keys per log must not see the old rows.
        measurementValveRepository.flush();
        measurementBrakesRepository.flush();

        valves.forEach(valve -> valve.setLog(log));
        measurementValveRepository.saveAll(valves);
        if (brakes != null) {
            brakes.setLog(log);
            measurementBrakesRepository.save(brakes);
        }
        if (chain != null) {
            chain.setLog(log);
            measurementChainRepository.save(chain);
        }
        if (tires != null) {
            tires.setLog(log);
            measurementTiresRepository.save(tires);
        }
    }

    @Override
    public MaintenanceMeasurementsResponse measurementsOf(MaintenanceLog log) {
        VehicleSpecification spec = specificationOf(log.getVehicle().getId());
        return response(measurementValveRepository.findAllByLog_Id(log.getId()),
                measurementBrakesRepository.findByLog_Id(log.getId()).orElse(null),
                measurementChainRepository.findByLog_Id(log.getId()).orElse(null),
                measurementTiresRepository.findByLog_Id(log.getId()).orElse(null),
                spec);
    }

    @Override
    public Map<Long, MaintenanceMeasurementsResponse> measurementsByLog(Long vehicleId) {
        VehicleSpecification spec = specificationOf(vehicleId);
        Map<Long, List<MeasurementValve>> valves = measurementValveRepository.findAllByLog_Vehicle_Id(vehicleId)
                .stream().collect(Collectors.groupingBy(valve -> valve.getLog().getId()));
        Map<Long, MeasurementBrakes> brakes = byLogId(measurementBrakesRepository.findAllByLog_Vehicle_Id(vehicleId),
                MeasurementBrakes::getLog);
        Map<Long, MeasurementChain> chains = byLogId(measurementChainRepository.findAllByLog_Vehicle_Id(vehicleId),
                MeasurementChain::getLog);
        Map<Long, MeasurementTires> tires = byLogId(measurementTiresRepository.findAllByLog_Vehicle_Id(vehicleId),
                MeasurementTires::getLog);
        Set<Long> logIds = new HashSet<>(valves.keySet());
        logIds.addAll(brakes.keySet());
        logIds.addAll(chains.keySet());
        logIds.addAll(tires.keySet());
        Map<Long, MaintenanceMeasurementsResponse> result = new HashMap<>();
        for (Long logId : logIds) {
            result.put(logId, response(valves.getOrDefault(logId, List.of()), brakes.get(logId), chains.get(logId),
                    tires.get(logId), spec));
        }
        return result;
    }

    private MaintenanceMeasurementsResponse response(List<MeasurementValve> valves, MeasurementBrakes brakes,
                                                     MeasurementChain chain, MeasurementTires tires,
                                                     VehicleSpecification spec) {
        if (valves.isEmpty() && brakes == null && chain == null && tires == null) {
            return null;
        }
        MaintenanceMeasurementsResponse response = new MaintenanceMeasurementsResponse();
        if (!valves.isEmpty()) {
            response.setValveClearance(valveResponse(valves, spec));
        }
        if (brakes != null) {
            response.setBrakes(brakesResponse(brakes, spec));
        }
        if (chain != null) {
            response.setChain(chainResponse(chain, spec));
        }
        if (tires != null) {
            response.setTires(tiresResponse(tires, spec));
        }
        return response;
    }

    private ValveClearanceMeasurementResponse valveResponse(List<MeasurementValve> valves,
                                                            VehicleSpecification spec) {
        List<ValveMeasurementResponse> responses = valves.stream()
                .sorted(VALVE_ORDER)
                .map(valve -> {
                    boolean intake = valve.getValve() == ValveKind.INTAKE;
                    return measurementMapper.toResponse(valve).measuredStatus(MeasurementStatusHelper.range(
                            valve.getMeasured(),
                            intake ? spec.getIntakeClearanceMin() : spec.getExhaustClearanceMin(),
                            intake ? spec.getIntakeClearanceMax() : spec.getExhaustClearanceMax()));
                })
                .toList();
        return new ValveClearanceMeasurementResponse(responses);
    }

    private BrakeMeasurementResponse brakesResponse(MeasurementBrakes brakes, VehicleSpecification spec) {
        return measurementMapper.toResponse(brakes)
                .padFrontStatus(MeasurementStatusHelper.range(brakes.getPadFront(), spec.getBrakePadMinFront(), null))
                .padRearStatus(MeasurementStatusHelper.range(brakes.getPadRear(), spec.getBrakePadMinRear(), null))
                .discFrontStatus(MeasurementStatusHelper.range(brakes.getDiscFront(), spec.getBrakeDiscMinFront(),
                        null))
                .discRearStatus(MeasurementStatusHelper.range(brakes.getDiscRear(), spec.getBrakeDiscMinRear(),
                        null));
    }

    private ChainMeasurementResponse chainResponse(MeasurementChain chain, VehicleSpecification spec) {
        return measurementMapper.toResponse(chain)
                .slackStatus(MeasurementStatusHelper.range(chain.getSlack(), spec.getChainSlackMin(),
                        spec.getChainSlackMax()))
                .wearLengthStatus(MeasurementStatusHelper.chainWear(chain.getWearLength(), chain.getWearLinks(),
                        spec.getChainWearMaxLength(), spec.getChainWearLinks()));
    }

    private TireMeasurementResponse tiresResponse(MeasurementTires tires, VehicleSpecification spec) {
        return measurementMapper.toResponse(tires)
                .pressureFrontStatus(MeasurementStatusHelper.pressure(tires.getPressureFront(),
                        spec.getTirePressureFront()))
                .pressureRearStatus(MeasurementStatusHelper.pressure(tires.getPressureRear(),
                        spec.getTirePressureRear()))
                .treadFrontStatus(MeasurementStatusHelper.range(tires.getTreadFront(), spec.getTreadDepthMin(), null))
                .treadRearStatus(MeasurementStatusHelper.range(tires.getTreadRear(), spec.getTreadDepthMin(), null));
    }

    private List<MeasurementValve> valvesOf(ValveClearanceMeasurementRequest request) {
        if (request == null) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        List<MeasurementValve> valves = new ArrayList<>();
        for (ValveMeasurementRequest valve : request.getValves()) {
            if (!seen.add(valve.getCylinder() + "/" + valve.getValve() + "/" + valve.getIndex())) {
                throw new InvalidMeasurementException("Valve " + valve.getValve() + " " + valve.getIndex()
                        + " of cylinder " + valve.getCylinder() + " is listed twice");
            }
            requireScale(valve.getMeasured(), MM_SCALE, "measured");
            requireScale(valve.getShimOld(), MM_SCALE, "shimOld");
            requireScale(valve.getShimNew(), MM_SCALE, "shimNew");
            valves.add(measurementMapper.toEntity(valve));
        }
        return valves;
    }

    private MeasurementBrakes brakesOf(BrakeMeasurementRequest request) {
        if (request == null) {
            return null;
        }
        requireScale(MM_SCALE, request.getPadFront(), request.getPadRear(), request.getDiscFront(),
                request.getDiscRear());
        MeasurementBrakes brakes = measurementMapper.toEntity(request);
        brakes.setDiscConditionFront(text(brakes.getDiscConditionFront()));
        brakes.setDiscConditionRear(text(brakes.getDiscConditionRear()));
        brakes.setFluidCondition(text(brakes.getFluidCondition()));
        return brakes;
    }

    private MeasurementChain chainOf(ChainMeasurementRequest request) {
        if (request == null) {
            return null;
        }
        requireScale(MM_SCALE, request.getSlack(), request.getWearLength());
        MeasurementChain chain = measurementMapper.toEntity(request);
        chain.setSprocketCondition(text(chain.getSprocketCondition()));
        chain.setRearSprocketCondition(text(chain.getRearSprocketCondition()));
        return chain;
    }

    private MeasurementTires tiresOf(TireMeasurementRequest request) {
        if (request == null) {
            return null;
        }
        requireScale(BAR_SCALE, request.getPressureFront(), request.getPressureRear());
        requireScale(MM_SCALE, request.getTreadFront(), request.getTreadRear());
        MeasurementTires tires = measurementMapper.toEntity(request);
        tires.setConditionFront(text(tires.getConditionFront()));
        tires.setConditionRear(text(tires.getConditionRear()));
        return tires;
    }

    private static void validate(VehicleSpecifications request) {
        requireScale(MM_SCALE, request.getIntakeClearanceMin(), request.getIntakeClearanceMax(),
                request.getExhaustClearanceMin(), request.getExhaustClearanceMax(), request.getShimStep(),
                request.getShimMin(), request.getShimMax(), request.getBrakePadMinFront(),
                request.getBrakePadMinRear(), request.getBrakeDiscMinFront(), request.getBrakeDiscMinRear(),
                request.getChainSlackMin(), request.getChainSlackMax(), request.getChainWearMaxLength(),
                request.getTreadDepthMin());
        requireScale(BAR_SCALE, request.getTirePressureFront(), request.getTirePressureRear());
        requireOrdered(request.getIntakeClearanceMin(), request.getIntakeClearanceMax(), "intakeClearance");
        requireOrdered(request.getExhaustClearanceMin(), request.getExhaustClearanceMax(), "exhaustClearance");
        requireOrdered(request.getShimMin(), request.getShimMax(), "shim");
        requireOrdered(request.getChainSlackMin(), request.getChainSlackMax(), "chainSlack");
    }

    private static void requireOrdered(BigDecimal min, BigDecimal max, String field) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new InvalidMeasurementException(field + "Min must not be greater than " + field + "Max");
        }
    }

    private static void requireScale(int maxScale, BigDecimal... values) {
        for (BigDecimal value : values) {
            requireScale(value, maxScale, "value");
        }
    }

    private static void requireScale(BigDecimal value, int maxScale, String field) {
        if (value != null && value.stripTrailingZeros().scale() > maxScale) {
            throw new InvalidMeasurementException(field + " must not have more than " + maxScale
                    + " decimal places");
        }
    }

    /** Free text without surrounding blanks; blank becomes null. */
    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static MeasurementSource source(MaintenanceLog log) {
        return new MeasurementSource(log.getId(), log.getPerformedAt(), log.getMileageAtPerformed());
    }

    private static <T> Optional<T> latest(List<T> measurements, Function<T, MaintenanceLog> log) {
        return measurements.stream().min(Comparator.comparing(log, LATEST_FIRST));
    }

    private static <T> Map<Long, T> byLogId(List<T> measurements, Function<T, MaintenanceLog> log) {
        return measurements.stream().collect(Collectors.toMap(m -> log.apply(m).getId(), Function.identity()));
    }

    /** Target values of the vehicle; an empty object if none are stored, so every status is UNKNOWN. */
    private VehicleSpecification specificationOf(Long vehicleId) {
        return vehicleSpecificationRepository.findById(vehicleId).orElseGet(VehicleSpecification::new);
    }

    /** Vehicles of other users are treated like non-existent ones (404), so foreign ids cannot be probed. */
    private Optional<Vehicle> findOwnVehicle(Long vehicleId) {
        return vehicleRepository.findByIdAndUser_Id(vehicleId, currentUserComponent.getCurrentUserId());
    }
}

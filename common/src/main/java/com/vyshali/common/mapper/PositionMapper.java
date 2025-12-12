package com.vyshali.common.mapper;

import com.vyshali.fxanalyzer.common.dto.HedgePositionDto;
import com.vyshali.fxanalyzer.common.entity.Exposure;
import com.vyshali.fxanalyzer.common.entity.Position;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MapStruct mapper for Position entity to DTO conversion
 */
@Mapper(componentModel = "spring")
public interface PositionMapper {

    PositionMapper INSTANCE = Mappers.getMapper(PositionMapper.class);

    @Mapping(source = "product.identifierType", target = "identifierType")
    @Mapping(source = "product.identifier", target = "identifier")
    @Mapping(source = "product.ticker", target = "ticker")
    @Mapping(source = "product.securityDescription", target = "securityDescription")
    @Mapping(source = "product.issueCurrency", target = "issueCurrency")
    @Mapping(source = "product.settlementCurrency", target = "settlementCurrency")
    @Mapping(source = "priceUsed", target = "price")
    @Mapping(source = "fxRateUsed", target = "fxRate")
    @Mapping(source = ".", target = "genericExposures", qualifiedByName = "mapGenericExposures")
    @Mapping(source = ".", target = "specificExposures", qualifiedByName = "mapSpecificExposures")
    @Mapping(expression = "java(position.isLong())", target = "isLong")
    HedgePositionDto toDto(Position position);

    List<HedgePositionDto> toDtoList(List<Position> positions);

    @Named("mapGenericExposures")
    default List<HedgePositionDto.ExposureDto> mapGenericExposures(Position position) {
        if (position.getExposures() == null) return List.of();
        return position.getExposures().stream()
                .filter(e -> "GENERIC".equals(e.getExposureType()))
                .map(this::toExposureDto)
                .collect(Collectors.toList());
    }

    @Named("mapSpecificExposures")
    default List<HedgePositionDto.ExposureDto> mapSpecificExposures(Position position) {
        if (position.getExposures() == null) return List.of();
        return position.getExposures().stream()
                .filter(e -> "SPECIFIC".equals(e.getExposureType()))
                .map(this::toExposureDto)
                .collect(Collectors.toList());
    }

    @Mapping(source = "currency", target = "currency")
    @Mapping(source = "weightPercent", target = "weightPercent")
    @Mapping(source = "exposureAmountBase", target = "exposureAmount")
    HedgePositionDto.ExposureDto toExposureDto(Exposure exposure);
}

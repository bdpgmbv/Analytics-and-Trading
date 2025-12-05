package com.vyshali.positionloader.mapper;

/*
 * 12/05/2025 - 11:02 AM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.PositionDetailDTO;
// Assuming you have a Position Entity (JPA or otherwise).
// Since we use JdbcTemplate, we often map ResultSets, but MapStruct is great for DTO-to-DTO conversions.
// Example: Mapping Upstream DTO to Internal Domain Object.
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PositionMapper {

    // Example: If you had an Entity class
    /*
    @Mapping(target = "externalRefId", source = "refId")
    @Mapping(target = "auditTime", expression = "java(java.time.LocalDateTime.now())")
    PositionEntity toEntity(PositionDetailDTO dto);
    */

    // Example: Cloning/Enriching DTOs
    @Mapping(target = "txnType", defaultValue = "LOAD")
    PositionDetailDTO copyWithDefault(PositionDetailDTO source);
}

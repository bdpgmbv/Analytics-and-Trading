package com.vyshali.positionloader.mapper;

/*
 * 12/08/2025 - 3:28 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.PositionDetailDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SnapshotMapper {

    // This method is used by the Service to sanitize inputs
    @Mapping(target = "quantity", source = "quantity", qualifiedByName = "sanitizeQuantity")
    PositionDetailDTO sanitize(PositionDetailDTO dto);

    List<PositionDetailDTO> sanitizeList(List<PositionDetailDTO> list);

    @Named("sanitizeQuantity")
    default BigDecimal sanitizeQuantity(BigDecimal qty) {
        // Logic: e.g., Replace nulls with Zero
        return qty != null ? qty : BigDecimal.ZERO;
    }
}

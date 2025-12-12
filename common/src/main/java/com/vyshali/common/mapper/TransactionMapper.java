package com.vyshali.common.mapper;

import com.vyshali.fxanalyzer.common.dto.TransactionDto;
import com.vyshali.fxanalyzer.common.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.util.List;

/**
 * MapStruct mapper for Transaction entity to DTO conversion
 */
@Mapper(componentModel = "spring")
public interface TransactionMapper {

    TransactionMapper INSTANCE = Mappers.getMapper(TransactionMapper.class);

    @Mapping(source = "account.accountNumber", target = "portfolioId")
    @Mapping(source = "product.identifierType", target = "identifierType")
    @Mapping(source = "product.identifier", target = "identifier")
    @Mapping(source = "product.securityDescription", target = "securityDescription")
    @Mapping(source = "product.issueCurrency", target = "issueCurrency")
    @Mapping(source = "product.settlementCurrency", target = "settlementCurrency")
    @Mapping(source = "transactionType", target = "buySell")
    @Mapping(expression = "java(isCurrentDayTrade(transaction))", target = "isCurrentDayTrade")
    TransactionDto toDto(Transaction transaction);

    List<TransactionDto> toDtoList(List<Transaction> transactions);

    default Boolean isCurrentDayTrade(Transaction transaction) {
        return transaction.getTradeDate() != null && 
               transaction.getTradeDate().equals(LocalDate.now());
    }
}

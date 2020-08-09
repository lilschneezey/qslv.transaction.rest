package qslv.transaction.rest.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import qslv.transaction.request.TransactionSearchRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.TransactionSearchResponse;
import qslv.transaction.rest.JdbcDao;
import qslv.transaction.rest.TransactionService;

@ExtendWith(MockitoExtension.class)
@RunWith(JUnitPlatform.class)
class UnitTransactionServiceTest_findReservation {
	@Mock 
	JdbcDao dao;
	TransactionService service = new TransactionService();
	
	@BeforeEach
	public void setup() {
		service.setJdbcDao(dao);
	}

	//-------------------------------------
	// findReservation
	//-------------------------------------
	
	@Test
	public void testFindReservation_byTransaction_success() {
		assertTrue ( false );
		//TODO this is all just copied and needs to be corrected
		TransactionSearchRequest request = new TransactionSearchRequest();
		request.setTransactionUuid(UUID.randomUUID());
		
		TransactionResource setupResource = new TransactionResource();
		setupResource.setTransactionUuid(UUID.randomUUID());
		setupResource.setAccountNumber("123781923123");
		setupResource.setDebitCardNumber("126743812673981623");
		setupResource.setInsertTimestamp(new Timestamp(Instant.now().toEpochMilli()));
		setupResource.setReservationUuid(UUID.randomUUID());
		setupResource.setRequestUuid(UUID.randomUUID());
		setupResource.setRunningBalanceAmount(99999L);
		setupResource.setTransactionAmount(-2323L);
		setupResource.setTransactionMetaDataJson("{etc, etc}");
		setupResource.setTransactionTypeCode(TransactionResource.RESERVATION_CANCEL);
			
		when(dao.findTransaction( any(UUID.class) )).thenReturn(setupResource);
		
		TransactionSearchResponse result = service.findTransaction(request);
		verify(dao).findTransaction(any(UUID.class));
		
		assertNotNull( result.getTransactions() );
		assertTrue( result.getTransactions().size() > 0);
		assertSame( setupResource, result.getTransactions().get(0));
	}
	
	@Test
	public void testFindReservation_byTransaction_notFound() {
		TransactionSearchRequest request = new TransactionSearchRequest();
		request.setTransactionUuid(UUID.randomUUID());
			
		when(dao.findTransaction( any(UUID.class) )).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,"Not Found"));
		
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { service.findTransaction(request); });
		verify(dao).findTransaction(any(UUID.class));
		
		assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
	}
	
	@Test
	public void testFindReservations_byReservation_success() {
		TransactionSearchRequest request = new TransactionSearchRequest();
		request.setReservationUuid(UUID.randomUUID());
		
		TransactionResource setupResource = new TransactionResource();
		setupResource.setTransactionUuid(UUID.randomUUID());
		setupResource.setAccountNumber("123781923123");
		setupResource.setDebitCardNumber("126743812673981623");
		setupResource.setInsertTimestamp(new Timestamp(Instant.now().toEpochMilli()));
		setupResource.setReservationUuid(UUID.randomUUID());
		setupResource.setRequestUuid(UUID.randomUUID());
		setupResource.setRunningBalanceAmount(99999L);
		setupResource.setTransactionAmount(-2323L);
		setupResource.setTransactionMetaDataJson("{etc, etc}");
		setupResource.setTransactionTypeCode(TransactionResource.RESERVATION_CANCEL);
		List<TransactionResource> setupList = Collections.singletonList(setupResource);
			
		when(dao.findRelatedToReservation( any(UUID.class) )).thenReturn(setupList);
		
		TransactionSearchResponse result = service.findTransaction(request);
		verify(dao).findRelatedToReservation(any(UUID.class));
		
		assertNotNull( result.getTransactions() );
		assertTrue( result.getTransactions().size() > 0);
		assertSame( setupList, result.getTransactions());
	}
	
	@Test
	public void testFindReservations_byReservation_failure() {
		TransactionSearchRequest request = new TransactionSearchRequest();
		request.setReservationUuid(UUID.randomUUID());
			
		when(dao.findRelatedToReservation( any(UUID.class) )).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,"Not Found"));
		
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { service.findTransaction(request); });
		verify(dao).findRelatedToReservation(any(UUID.class));
		
		assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
	}

}

package qslv.transaction.rest.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import qslv.transaction.request.CancelReservationRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.CancelReservationResponse;
import qslv.transaction.rest.JdbcDao;
import qslv.transaction.rest.TransactionService;

@ExtendWith(MockitoExtension.class)
class UnitTransactionServiceTest_cancelReservation {
	@Mock 
	JdbcDao dao;
	TransactionService service = new TransactionService();
	
	@BeforeEach
	public void setup() {
		service.setJdbcDao(dao);
	}

	//-------------------------------------
	// cancelReservation
	//-------------------------------------
	@Test
	public void testCancelReservation_alreadyPresent() {
		CancelReservationRequest request = new CancelReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		request.setAccountNumber("7328429347");
		
		TransactionResource setupResponse = new TransactionResource();
		setupResponse.setTransactionUuid(UUID.randomUUID());
		setupResponse.setAccountNumber("123781923123");
		setupResponse.setDebitCardNumber("126743812673981623");
		setupResponse.setInsertTimestamp(new Timestamp(Instant.now().toEpochMilli()));
		setupResponse.setRequestUuid(UUID.randomUUID());
		setupResponse.setRunningBalanceAmount(10000L);
		setupResponse.setTransactionAmount(-2323);
		setupResponse.setTransactionMetaDataJson("{etc, etc}");
		setupResponse.setTransactionTypeCode(TransactionResource.RESERVATION);
			
		when(dao.checkIdempotency( any(UUID.class), anyString() )).thenReturn(setupResponse);
		CancelReservationResponse result = service.cancelReservation(request);
		verify(dao).checkIdempotency(any(UUID.class),  anyString() );
		assert(result.getStatus()==CancelReservationResponse.SUCCESS);
		assert(result.getResource().getTransactionUuid().equals(setupResponse.getTransactionUuid()));
	}
	
	@Test
	public void testCancelReservation_success() {
		CancelReservationRequest request = new CancelReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		request.setAccountNumber("7328429347");
		
		TransactionResource setupReservation = new TransactionResource();
		setupReservation.setTransactionUuid(UUID.randomUUID());
		setupReservation.setAccountNumber("123781923123");
		setupReservation.setDebitCardNumber("126743812673981623");
		setupReservation.setInsertTimestamp(new Timestamp(Instant.now().toEpochMilli()));
		setupReservation.setRequestUuid(UUID.randomUUID());
		setupReservation.setRunningBalanceAmount(99999L);
		setupReservation.setTransactionAmount(-2323L);
		setupReservation.setTransactionMetaDataJson("{etc, etc}");
		setupReservation.setTransactionTypeCode(TransactionResource.RESERVATION);
			
		when(dao.checkIdempotency( any(UUID.class), anyString()  )).thenReturn(null);
		when(dao.findReservation(any(UUID.class))).thenReturn(setupReservation);
		doNothing().when(dao).verifyReservationOpen(isA(UUID.class));
		when(dao.selectBalanceForUpdate(any(String.class))).thenReturn(11111L);
		doNothing().when(dao).insertCommitOrCancel(isA(TransactionResource.class));
		
		CancelReservationResponse result = service.cancelReservation(request);
		verify(dao).checkIdempotency(any(UUID.class), anyString() );
		verify(dao).findReservation(any(UUID.class));
		verify(dao).verifyReservationOpen(any(UUID.class));
		verify(dao).selectBalanceForUpdate(any(String.class));
		verify(dao).insertCommitOrCancel(any(TransactionResource.class));
		
		assert(result.getStatus()==CancelReservationResponse.SUCCESS);
		assert(result.getResource().getAccountNumber().equals(setupReservation.getAccountNumber()));
		assert(result.getResource().getDebitCardNumber().equals(setupReservation.getDebitCardNumber()));
		assert(result.getResource().getRequestUuid().equals(request.getRequestUuid()));
		assert(result.getResource().getReservationUuid().equals(request.getReservationUuid()));
		assert(result.getResource().getTransactionAmount() == 2323L );
		assert(result.getResource().getRunningBalanceAmount() == 11111L + 2323L);
		assert(result.getResource().getTransactionMetaDataJson().equals(request.getTransactionMetaDataJson()));
		assert(result.getResource().getTransactionTypeCode()== TransactionResource.RESERVATION_CANCEL);
	}
	
	@Test
	public void testCancelReservation_reservationNotFound() {
		CancelReservationRequest request = new CancelReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		request.setAccountNumber("7328429347");
		
		TransactionResource setupReservation = new TransactionResource();
		setupReservation.setTransactionUuid(UUID.randomUUID());
		setupReservation.setAccountNumber("123781923123");
		setupReservation.setDebitCardNumber("126743812673981623");
		setupReservation.setInsertTimestamp(new Timestamp(Instant.now().toEpochMilli()));
		setupReservation.setRequestUuid(UUID.randomUUID());
		setupReservation.setRunningBalanceAmount(99999L);
		setupReservation.setTransactionAmount(-2323L);
		setupReservation.setTransactionMetaDataJson("{etc, etc}");
		setupReservation.setTransactionTypeCode(TransactionResource.RESERVATION);
			
		when(dao.checkIdempotency( any(UUID.class), anyString()  )).thenReturn(null);
		when(dao.findReservation(any(UUID.class))).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,"string") );
		
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { service.cancelReservation(request); });
		verify(dao).checkIdempotency(any(UUID.class), anyString() );
		assert(ex.getStatus() == HttpStatus.NOT_FOUND);
	}
	@Test
	public void testCancelReservation_reservationAlreadyCommitted() {
		CancelReservationRequest request = new CancelReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		request.setAccountNumber("7328429347");
		
		TransactionResource setupReservation = new TransactionResource();
		setupReservation.setTransactionUuid(UUID.randomUUID());
		setupReservation.setAccountNumber("123781923123");
		setupReservation.setDebitCardNumber("126743812673981623");
		setupReservation.setInsertTimestamp(new Timestamp(Instant.now().toEpochMilli()));
		setupReservation.setRequestUuid(UUID.randomUUID());
		setupReservation.setRunningBalanceAmount(99999L);
		setupReservation.setTransactionAmount(-2323L);
		setupReservation.setTransactionMetaDataJson("{etc, etc}");
		setupReservation.setTransactionTypeCode(TransactionResource.RESERVATION);
			
		when(dao.checkIdempotency( any(UUID.class), anyString()  )).thenReturn(null);
		when(dao.findReservation(any(UUID.class))).thenReturn(setupReservation);
		doThrow(new ResponseStatusException(HttpStatus.CONFLICT,"string"))
			.when(dao).verifyReservationOpen(isA(UUID.class));
		
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { service.cancelReservation(request); });
		verify(dao).checkIdempotency(any(UUID.class), anyString() );
		verify(dao).findReservation(any(UUID.class));
		assert(ex.getStatus() == HttpStatus.CONFLICT);
	}
	@Test
	public void testCancelReservation_upsertBalanceFails() {
		CancelReservationRequest request = new CancelReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		request.setAccountNumber("7328429347");
		
		TransactionResource setupReservation = new TransactionResource();
		setupReservation.setTransactionUuid(UUID.randomUUID());
		setupReservation.setAccountNumber("123781923123");
		setupReservation.setDebitCardNumber("126743812673981623");
		setupReservation.setInsertTimestamp(new Timestamp(Instant.now().toEpochMilli()));
		setupReservation.setRequestUuid(UUID.randomUUID());
		setupReservation.setRunningBalanceAmount(99999L);
		setupReservation.setTransactionAmount(-2323L);
		setupReservation.setTransactionMetaDataJson("{etc, etc}");
		setupReservation.setTransactionTypeCode(TransactionResource.RESERVATION);
			
		when(dao.checkIdempotency( any(UUID.class), anyString()  )).thenReturn(null);
		when(dao.findReservation(any(UUID.class))).thenReturn(setupReservation);
		doNothing().when(dao).verifyReservationOpen(isA(UUID.class));
		when(dao.selectBalanceForUpdate(any(String.class)))
			.thenThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"string"));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { service.cancelReservation(request); });
		verify(dao).checkIdempotency(any(UUID.class), anyString() );
		verify(dao).findReservation(any(UUID.class));
		verify(dao).verifyReservationOpen(any(UUID.class));
		assert(ex.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
	}
	@Test
	public void testCancelReservation_insertCommitOrCancelFails() {
		CancelReservationRequest request = new CancelReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		request.setAccountNumber("7328429347");
		
		TransactionResource setupReservation = new TransactionResource();
		setupReservation.setTransactionUuid(UUID.randomUUID());
		setupReservation.setAccountNumber("123781923123");
		setupReservation.setDebitCardNumber("126743812673981623");
		setupReservation.setInsertTimestamp(new Timestamp(Instant.now().toEpochMilli()));
		setupReservation.setRequestUuid(UUID.randomUUID());
		setupReservation.setRunningBalanceAmount(99999L);
		setupReservation.setTransactionAmount(-2323L);
		setupReservation.setTransactionMetaDataJson("{etc, etc}");
		setupReservation.setTransactionTypeCode(TransactionResource.RESERVATION);
			
		when(dao.checkIdempotency( any(UUID.class), anyString()  )).thenReturn(null);
		when(dao.findReservation(any(UUID.class))).thenReturn(setupReservation);
		doNothing().when(dao).verifyReservationOpen(isA(UUID.class));
		when(dao.selectBalanceForUpdate(any(String.class))).thenReturn(11111L);
		doThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"string"))
			.when(dao).insertCommitOrCancel(isA(TransactionResource.class));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { service.cancelReservation(request); });
		verify(dao).checkIdempotency(any(UUID.class), anyString() );
		verify(dao).findReservation(any(UUID.class));
		verify(dao).verifyReservationOpen(any(UUID.class));
		verify(dao).selectBalanceForUpdate(any(String.class));
		assert(ex.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
	}

}

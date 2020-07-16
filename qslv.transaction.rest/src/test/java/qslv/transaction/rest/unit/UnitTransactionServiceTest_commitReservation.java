package qslv.transaction.rest.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
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

import qslv.transaction.request.CommitReservationRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.CommitReservationResponse;
import qslv.transaction.rest.TransactionDAO;
import qslv.transaction.rest.TransactionService;

@ExtendWith(MockitoExtension.class)
class UnitTransactionServiceTest_commitReservation {
	@Mock 
	TransactionDAO dao;
	TransactionService service = new TransactionService();
	
	@BeforeEach
	public void setup() {
		service.setDao(dao);
	}

	//-------------------------------------
	// commitReservation
	//-------------------------------------
	@Test
	public void testCommitReservation_alreadyPresent() {
		CommitReservationRequest request = new CommitReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionAmount(-2323);
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		
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
			
		when(dao.checkIdempotency( any(UUID.class) )).thenReturn(setupResponse);
		CommitReservationResponse result = service.commitReservation(request);
		verify(dao).checkIdempotency(any(UUID.class));
		assert(result.getStatus()==CommitReservationResponse.ALREADY_PRESENT);
		assert(result.getResource().getTransactionUuid().equals(setupResponse.getTransactionUuid()));
	}
	
	@Test
	public void testCommitReservation_successTransactionAmountsTheSame() {
		CommitReservationRequest request = new CommitReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionAmount(-2323);
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		
		TransactionResource setupReservation = new TransactionResource();
		setupReservation.setTransactionUuid(UUID.randomUUID());
		setupReservation.setAccountNumber("123781923123");
		setupReservation.setDebitCardNumber("126743812673981623");
		setupReservation.setInsertTimestamp(new Timestamp(Instant.now().toEpochMilli()));
		setupReservation.setRequestUuid(UUID.randomUUID());
		setupReservation.setRunningBalanceAmount(99999L);
		setupReservation.setTransactionAmount(-2323);
		setupReservation.setTransactionMetaDataJson("{etc, etc}");
		setupReservation.setTransactionTypeCode(TransactionResource.RESERVATION);
			
		when(dao.checkIdempotency( any(UUID.class) )).thenReturn(null);
		when(dao.findReservation(any(UUID.class))).thenReturn(setupReservation);
		doNothing().when(dao).verifyReservationOpen(isA(UUID.class));
		when(dao.selectBalanceForUpdate(any(String.class))).thenReturn(11111L);
		doNothing().when(dao).insertTransaction(isA(TransactionResource.class));
		
		CommitReservationResponse result = service.commitReservation(request);
		verify(dao).checkIdempotency(any(UUID.class));
		verify(dao).findReservation(any(UUID.class));
		verify(dao).verifyReservationOpen(any(UUID.class));
		verify(dao).selectBalanceForUpdate(any(String.class));
		verify(dao).insertTransaction(any(TransactionResource.class));
		
		assert(result.getStatus()==CommitReservationResponse.SUCCESS);
		assert(result.getResource().getAccountNumber().equals(setupReservation.getAccountNumber()));
		assert(result.getResource().getDebitCardNumber().equals(setupReservation.getDebitCardNumber()));
		assert(result.getResource().getRequestUuid().equals(request.getRequestUuid()));
		assert(result.getResource().getReservationUuid().equals(request.getReservationUuid()));
		assert(result.getResource().getTransactionAmount() == 0L);
		assert(result.getResource().getRunningBalanceAmount() == 11111L);
		assert(result.getResource().getTransactionMetaDataJson().equals(request.getTransactionMetaDataJson()));
		assert(result.getResource().getTransactionTypeCode()== TransactionResource.RESERVATION_COMMIT);
	}
	
	@Test
	public void testCommitReservation_successTransactionAmountsDifferent() {
		CommitReservationRequest request = new CommitReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionAmount(-3333L);
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		
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
			
		when(dao.checkIdempotency( any(UUID.class) )).thenReturn(null);
		when(dao.findReservation(any(UUID.class))).thenReturn(setupReservation);
		doNothing().when(dao).verifyReservationOpen(isA(UUID.class));
		when(dao.selectBalanceForUpdate(any(String.class))).thenReturn(11111L);
		doNothing().when(dao).insertTransaction(isA(TransactionResource.class));
		
		CommitReservationResponse result = service.commitReservation(request);
		verify(dao).checkIdempotency(any(UUID.class));
		verify(dao).findReservation(any(UUID.class));
		verify(dao).verifyReservationOpen(any(UUID.class));
		verify(dao).selectBalanceForUpdate(any(String.class));
		verify(dao).insertTransaction(any(TransactionResource.class));
		
		assert(result.getStatus()==CommitReservationResponse.SUCCESS);
		assert(result.getResource().getAccountNumber().equals(setupReservation.getAccountNumber()));
		assert(result.getResource().getDebitCardNumber().equals(setupReservation.getDebitCardNumber()));
		assert(result.getResource().getRequestUuid().equals(request.getRequestUuid()));
		assert(result.getResource().getReservationUuid().equals(request.getReservationUuid()));
		assert(result.getResource().getTransactionAmount() == -3333L + 2323L);
		assert(result.getResource().getRunningBalanceAmount() == 11111L - 3333L + 2323L);
		assert(result.getResource().getTransactionMetaDataJson().equals(request.getTransactionMetaDataJson()));
		assert(result.getResource().getTransactionTypeCode()== TransactionResource.RESERVATION_COMMIT);
	}
	@Test
	public void testCommitReservation_reservationNotFound() {
		CommitReservationRequest request = new CommitReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionAmount(-3333L);
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		
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
			
		when(dao.checkIdempotency( any(UUID.class) )).thenReturn(null);
		when(dao.findReservation(any(UUID.class))).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,"string") );
		
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { service.commitReservation(request); });
		verify(dao).checkIdempotency(any(UUID.class));
		assert(ex.getStatus() == HttpStatus.NOT_FOUND);
	}
	@Test
	public void testCommitReservation_reservationAlreadyCommitted() {
		CommitReservationRequest request = new CommitReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionAmount(-3333L);
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		
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
			
		when(dao.checkIdempotency( any(UUID.class) )).thenReturn(null);
		when(dao.findReservation(any(UUID.class))).thenReturn(setupReservation);
		doThrow(new ResponseStatusException(HttpStatus.CONFLICT,"string"))
			.when(dao).verifyReservationOpen(isA(UUID.class));
		
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { service.commitReservation(request); });
		verify(dao).checkIdempotency(any(UUID.class));
		verify(dao).findReservation(any(UUID.class));
		assert(ex.getStatus() == HttpStatus.CONFLICT);
	}
	@Test
	public void testCommitReservation_upsertBalanceFails() {
		CommitReservationRequest request = new CommitReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionAmount(-3333L);
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		
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
			
		when(dao.checkIdempotency( any(UUID.class) )).thenReturn(null);
		when(dao.findReservation(any(UUID.class))).thenReturn(setupReservation);
		doNothing().when(dao).verifyReservationOpen(isA(UUID.class));
		when(dao.selectBalanceForUpdate(any(String.class)))
			.thenThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"string"));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { service.commitReservation(request); });
		verify(dao).checkIdempotency(any(UUID.class));
		verify(dao).findReservation(any(UUID.class));
		verify(dao).verifyReservationOpen(any(UUID.class));
		assert(ex.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
	}
	@Test
	public void testCommitReservation_insertTransactionFails() {
		CommitReservationRequest request = new CommitReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionAmount(-3333L);
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		
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
			
		when(dao.checkIdempotency( any(UUID.class) )).thenReturn(null);
		when(dao.findReservation(any(UUID.class))).thenReturn(setupReservation);
		doNothing().when(dao).verifyReservationOpen(isA(UUID.class));
		when(dao.selectBalanceForUpdate(any(String.class))).thenReturn(11111L);
		doThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"string"))
			.when(dao).insertTransaction(isA(TransactionResource.class));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { service.commitReservation(request); });
		verify(dao).checkIdempotency(any(UUID.class));
		verify(dao).findReservation(any(UUID.class));
		verify(dao).verifyReservationOpen(any(UUID.class));
		verify(dao).selectBalanceForUpdate(any(String.class));
		assert(ex.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
	}

}

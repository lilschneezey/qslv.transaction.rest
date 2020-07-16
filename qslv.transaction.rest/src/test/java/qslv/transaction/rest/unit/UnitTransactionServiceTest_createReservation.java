package qslv.transaction.rest.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import qslv.transaction.request.TransactionRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.ReservationResponse;
import qslv.transaction.rest.TransactionDAO;
import qslv.transaction.rest.TransactionService;

@ExtendWith(MockitoExtension.class)
@RunWith(JUnitPlatform.class)
class UnitTransactionServiceTest_createReservation {
	@Mock 
	TransactionDAO dao;
	TransactionService service = new TransactionService();
	
	@BeforeEach
	public void setup() {
		service.setDao(dao);
	}

	//-------------------------------------
	// createReservation
	//-------------------------------------
	@Test
	public void testCreateReservation_requestAlreadyPresent() {
		TransactionRequest request = new TransactionRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setAccountNumber("1234567890234");
		request.setDebitCardNumber("1235671234678234");
		request.setTransactionAmount(-2323);
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		
		TransactionResource setupResult = new TransactionResource();
		setupResult.setTransactionUuid(UUID.randomUUID());
		ReservationResponse result;
		
		when(dao.checkIdempotency( any(UUID.class) )).thenReturn(setupResult).thenReturn(null);
		result = service.createReservation(request);
		verify(dao).checkIdempotency(any(UUID.class));
		assert(result.getStatus()==ReservationResponse.ALREADY_PRESENT);
		assert(result.getResource().getTransactionUuid().equals(setupResult.getTransactionUuid()));
	}

	@Test
	public void testCreateReservation_BalanceGTAmount() {
		TransactionRequest request = new TransactionRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setAccountNumber("1234567890234");
		request.setDebitCardNumber("1235671234678234");
		request.setTransactionAmount(-2323L);
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		
		TransactionResource setupResult = new TransactionResource();
		setupResult.setTransactionUuid(UUID.randomUUID());
		ReservationResponse result;
		
		when(dao.checkIdempotency( any(UUID.class) )).thenReturn(null);
		when(dao.selectBalanceForUpdate(any(String.class))).thenReturn(10000L);
		doNothing().when(dao).upsertBalance(isA(String.class), isA(Long.class));
		doNothing().when(dao).insertTransaction(isA(TransactionResource.class));
		result = service.createReservation(request);
		assert(result.getStatus() == ReservationResponse.SUCCESS);
		assert(result.getResource().getRequestUuid().equals(request.getRequestUuid()));
		assert(result.getResource().getAccountNumber().equals(request.getAccountNumber()));
		assert(result.getResource().getDebitCardNumber().equals(request.getDebitCardNumber()));
		assert(result.getResource().getTransactionMetaDataJson().equals(request.getTransactionMetaDataJson()));
		assert(result.getResource().getTransactionAmount() == request.getTransactionAmount() );

		assert(result.getResource().getRunningBalanceAmount() == 10000L+request.getTransactionAmount() );
		assert(result.getResource().getTransactionTypeCode() == TransactionResource.RESERVATION);
		// cannot test transaction uuid
		// cannot test insert_tsz
	}
	@Test
	public void testCreateReservation_BalanceEQAmount() {
		TransactionRequest request = new TransactionRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setAccountNumber("1234567890234");
		request.setDebitCardNumber("1235671234678234");
		request.setTransactionAmount(-10000L);
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		
		TransactionResource setupResult = new TransactionResource();
		setupResult.setTransactionUuid(UUID.randomUUID());
		ReservationResponse result;
		
		when(dao.checkIdempotency( any(UUID.class) )).thenReturn(null);
		when(dao.selectBalanceForUpdate(any(String.class))).thenReturn(10000L);
		doNothing().when(dao).upsertBalance(isA(String.class), isA(Long.class));
		doNothing().when(dao).insertTransaction(isA(TransactionResource.class));
		result = service.createReservation(request);
		assert(result.getStatus() == ReservationResponse.SUCCESS);
		assert(result.getResource().getRequestUuid().equals(request.getRequestUuid()));
		assert(result.getResource().getAccountNumber().equals(request.getAccountNumber()));
		assert(result.getResource().getDebitCardNumber().equals(request.getDebitCardNumber()));
		assert(result.getResource().getTransactionMetaDataJson().equals(request.getTransactionMetaDataJson()));
		assert(result.getResource().getTransactionAmount() == request.getTransactionAmount() );

		assert(result.getResource().getRunningBalanceAmount() == 10000L+request.getTransactionAmount() );
		assert(result.getResource().getTransactionTypeCode() == TransactionResource.RESERVATION);
		// cannot test transaction uuid
		// cannot test insert_tsz
	}
	@Test
	public void testCreateReservation_BalanceLTAmount() {
		TransactionRequest request = new TransactionRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setAccountNumber("1234567890234");
		request.setDebitCardNumber("1235671234678234");
		request.setTransactionAmount(-12323L);
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		
		TransactionResource setupResult = new TransactionResource();
		setupResult.setTransactionUuid(UUID.randomUUID());
		ReservationResponse result;
		
		when(dao.checkIdempotency( any(UUID.class) )).thenReturn(null);
		when(dao.selectBalanceForUpdate(any(String.class))).thenReturn(10000L);
		lenient().doNothing().when(dao).upsertBalance(isA(String.class), isA(Long.class));
		doNothing().when(dao).insertTransaction(isA(TransactionResource.class));
		result = service.createReservation(request);
		assert(result.getStatus() == ReservationResponse.INSUFFICIENT_FUNDS);
		assert(result.getResource().getRequestUuid().equals(request.getRequestUuid()));
		assert(result.getResource().getAccountNumber().equals(request.getAccountNumber()));
		assert(result.getResource().getDebitCardNumber().equals(request.getDebitCardNumber()));
		assert(result.getResource().getTransactionMetaDataJson().equals(request.getTransactionMetaDataJson()));
		assert(result.getResource().getTransactionAmount() == request.getTransactionAmount() );

		assert(result.getResource().getRunningBalanceAmount() == 10000L);
		assert(result.getResource().getTransactionTypeCode() == TransactionResource.REJECTED_TRANSACTION);
		// cannot test transaction uuid
		// cannot test insert_tsz
	}
	@Test
	public void testCreateReservation_upsertFails() {
		TransactionRequest request = new TransactionRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setAccountNumber("1234567890234");
		request.setDebitCardNumber("1235671234678234");
		request.setTransactionAmount(2323);
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		
		when(dao.checkIdempotency( any(UUID.class) )).thenReturn(null);		
		when(dao.selectBalanceForUpdate(any(String.class))).thenReturn(10000L);
		doThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"test throw."))
				.when(dao).upsertBalance(isA(String.class), isA(Long.class));
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()->{service.createReservation(request);});
		assert(ex.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
	}
	
	@Test
	public void testCreateReservation_insertFails() {
		TransactionRequest request = new TransactionRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setAccountNumber("1234567890234");
		request.setDebitCardNumber("1235671234678234");
		request.setTransactionAmount(2323);
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		
		when(dao.checkIdempotency( any(UUID.class) )).thenReturn(null);		
		when(dao.selectBalanceForUpdate(any(String.class))).thenReturn(10000L);
		doNothing().when(dao).upsertBalance(isA(String.class), isA(Long.class));
		doThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"test throw."))
				.when(dao).insertTransaction(isA(TransactionResource.class));
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()->{service.createReservation(request);});
		assert(ex.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
	}
	

}

package qslv.transaction.rest.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import qslv.transaction.response.TransactionResponse;
import qslv.transaction.rest.JdbcDao;
import qslv.transaction.rest.TransactionService;

@ExtendWith(MockitoExtension.class)
@RunWith(JUnitPlatform.class)
class UnitTransactionServiceTest_createTransaction {
	@Mock 
	JdbcDao dao;
	TransactionService service = new TransactionService();
	
	@BeforeEach
	public void setup() {
		service.setJdbcDao(dao);
	}
	
	//-------------------------------------
	// createTransaction
	//-------------------------------------
	@Test
	public void testCreateTransaction_requestAlreadyPresent() {
		TransactionRequest request = setup_request();
		
		TransactionResource setupResult = new TransactionResource();
		setupResult.setTransactionUuid(UUID.randomUUID());
		setupResult.setTransactionTypeCode(TransactionResource.NORMAL);
		TransactionResponse result;
		
		when(dao.checkIdempotency( any(UUID.class), anyString() )).thenReturn(setupResult);
		result = service.createTransaction(request);
		verify(dao).checkIdempotency(any(UUID.class), anyString());

		assertEquals(setupResult.getTransactionUuid(), result.getTransactions().get(0).getTransactionUuid());
		assertEquals(TransactionResponse.SUCCESS, result.getStatus());
	}
	
	@Test
	public void testCreateTransaction_success() {
		TransactionRequest request = setup_request();
		
		TransactionResource setupResult = new TransactionResource();
		setupResult.setTransactionUuid(UUID.randomUUID());
		TransactionResponse result;
		
		when(dao.checkIdempotency( any(UUID.class), anyString() )).thenReturn(null);
		when(dao.selectBalanceForUpdate(any(String.class))).thenReturn(10000L);
		doNothing().when(dao).upsertBalance(isA(String.class), isA(Long.class));
		doNothing().when(dao).insertTransaction(isA(TransactionResource.class));
		
		result = service.createTransaction(request);

		assertEquals(TransactionResponse.SUCCESS, result.getStatus());
		assertEquals(request.getAccountNumber(), result.getTransactions().get(0).getAccountNumber());
		assertEquals(request.getDebitCardNumber(), result.getTransactions().get(0).getDebitCardNumber());
		assertEquals(request.getRequestUuid(), result.getTransactions().get(0).getRequestUuid());
		assertEquals(request.getTransactionAmount(), result.getTransactions().get(0).getTransactionAmount());
		assertEquals(request.getTransactionMetaDataJson(), result.getTransactions().get(0).getTransactionMetaDataJson());
		assertEquals(request.getAccountNumber(), result.getTransactions().get(0).getAccountNumber());
		assertNull(result.getTransactions().get(0).getReservationUuid());
		assertEquals(10000L+request.getTransactionAmount(), result.getTransactions().get(0).getRunningBalanceAmount());
		assertEquals(TransactionResource.NORMAL, result.getTransactions().get(0).getTransactionTypeCode());

	}
	
	@Test
	public void testCreateTransaction_upsertFails() {
		TransactionRequest request = setup_request();
		
		when(dao.checkIdempotency( any(UUID.class), anyString() )).thenReturn(null);		
		when(dao.selectBalanceForUpdate(any(String.class))).thenReturn(10000L);
		doThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"test throw."))
				.when(dao).upsertBalance(isA(String.class), isA(Long.class));
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()->{service.createTransaction(request);});

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,ex.getStatus());
	}
	
	@Test
	public void testCreateTransaction_insertFails() {
		TransactionRequest request = setup_request();
		
		when(dao.checkIdempotency( any(UUID.class), anyString() )).thenReturn(null);		
		when(dao.selectBalanceForUpdate(any(String.class))).thenReturn(10000L);
		doNothing().when(dao).upsertBalance(isA(String.class), isA(Long.class));
		doThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"test throw."))
				.when(dao).insertTransaction(isA(TransactionResource.class));
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()->{service.createTransaction(request);});

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,ex.getStatus());
	}

	@Test void testCreateTransaction_NSF() {
		TransactionRequest request = setup_request();
		request.setAuthorizeAgainstBalance(true);
		request.setTransactionAmount(-2000L);
		
		when(dao.checkIdempotency( any(UUID.class), anyString() )).thenReturn(null);
		when(dao.selectBalanceForUpdate(any(String.class))).thenReturn(1000L);
		
		TransactionResponse result = service.createTransaction(request);
		assertEquals(TransactionResponse.INSUFFICIENT_FUNDS, result.getStatus());
		assertEquals(request.getAccountNumber(), result.getTransactions().get(0).getAccountNumber());
		assertEquals(request.getDebitCardNumber(), result.getTransactions().get(0).getDebitCardNumber());
		assertEquals(request.getRequestUuid(), result.getTransactions().get(0).getRequestUuid());
		assertEquals(request.getTransactionAmount(), result.getTransactions().get(0).getTransactionAmount());
		assertEquals(request.getTransactionMetaDataJson(), result.getTransactions().get(0).getTransactionMetaDataJson());
		assertEquals(request.getAccountNumber(), result.getTransactions().get(0).getAccountNumber());
		assertNull(result.getTransactions().get(0).getReservationUuid());
		assertEquals(1000L, result.getTransactions().get(0).getRunningBalanceAmount());
		assertEquals(TransactionResource.REJECTED_TRANSACTION, result.getTransactions().get(0).getTransactionTypeCode());
	}
	
	@Test void testCreateTransaction_ingnoreNSF() {
		TransactionRequest request = setup_request();
		request.setAuthorizeAgainstBalance(false);
		request.setTransactionAmount(-2000L);

		when(dao.checkIdempotency( any(UUID.class), anyString() )).thenReturn(null);
		when(dao.selectBalanceForUpdate(any(String.class))).thenReturn(1000L);
		
		TransactionResponse result = service.createTransaction(request);
		assertEquals(TransactionResponse.SUCCESS, result.getStatus());
		assertEquals(request.getAccountNumber(), result.getTransactions().get(0).getAccountNumber());
		assertEquals(request.getDebitCardNumber(), result.getTransactions().get(0).getDebitCardNumber());
		assertEquals(request.getRequestUuid(), result.getTransactions().get(0).getRequestUuid());
		assertEquals(request.getTransactionAmount(), result.getTransactions().get(0).getTransactionAmount());
		assertEquals(request.getTransactionMetaDataJson(), result.getTransactions().get(0).getTransactionMetaDataJson());
		assertEquals(request.getAccountNumber(), result.getTransactions().get(0).getAccountNumber());
		assertNull(result.getTransactions().get(0).getReservationUuid());
		assertEquals(-1000L, result.getTransactions().get(0).getRunningBalanceAmount());
		assertEquals(TransactionResource.NORMAL, result.getTransactions().get(0).getTransactionTypeCode());
	}
	
	private TransactionRequest setup_request() {
		TransactionRequest request = new TransactionRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setAccountNumber("1234567890234");
		request.setDebitCardNumber("1235671234678234");
		request.setTransactionAmount(-2323);
		request.setTransactionMetaDataJson("{\"value\":23498234}");
		request.setProtectAgainstOverdraft(false);
		request.setAuthorizeAgainstBalance(false);
		return request;
	}

}

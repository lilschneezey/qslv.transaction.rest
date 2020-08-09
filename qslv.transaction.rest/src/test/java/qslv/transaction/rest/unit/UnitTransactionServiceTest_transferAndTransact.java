package qslv.transaction.rest.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
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
import qslv.transaction.request.TransferAndTransactRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.TransactionResponse;
import qslv.transaction.response.TransferAndTransactResponse;
import qslv.transaction.rest.JdbcDao;
import qslv.transaction.rest.TransactionService;

@ExtendWith(MockitoExtension.class)
@RunWith(JUnitPlatform.class)
class UnitTransactionServiceTest_transferAndTransact {
	@Mock 
	JdbcDao dao;
	TransactionService service = new TransactionService();
	
	@BeforeEach
	public void setup() {
		service.setJdbcDao(dao);
	}
	
	@Test
	public void tandt_requestAlreadyPresent() {
		//--Prepare--------
		TransferAndTransactRequest request = setup_request();
		ArrayList<TransactionResource> list = new ArrayList<TransactionResource>();
		list.add(new TransactionResource());
		list.add(new TransactionResource());
		
		//--Setup--------
		when(dao.checkMultiIdempotency( any(UUID.class), anyString() )).thenReturn(list);
		
		//--Execute--------
		TransferAndTransactResponse result = service.transferAndTransact(request);

		//--Verify--------
		verify(dao).checkMultiIdempotency(any(UUID.class), anyString());

		assertEquals(TransactionResponse.SUCCESS, result.getStatus());
	}
	
	@Test
	public void tandt_onlyOneRequestAlreadyPresent() {
		//--Prepare--------
		TransferAndTransactRequest request = setup_request();
		
		//--Setup--------
		when(dao.checkMultiIdempotency( any(UUID.class), anyString() ))
			.thenReturn(Collections.singletonList(new TransactionResource()) );
		
		//--Execute--------
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()->{ service.transferAndTransact(request); });

		//--Verify--------
		verify(dao).checkMultiIdempotency(any(UUID.class), anyString());

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
	}
	
	@Test
	public void testCreateTransaction_success() {
		long starting_balance = 10000L;
		long transfer_amount =  -10000L;
		long transaction_amount = -15000L;
		//--Prepare--------
		TransferAndTransactRequest request = setup_request();
		request.getTransferReservation().setTransactionAmount(transfer_amount);
		request.getTransactionRequest().setTransactionAmount(transaction_amount);
		
		//--Setup--------
		when(dao.checkMultiIdempotency( any(UUID.class), anyString() )).thenReturn(Collections.emptyList());
		doReturn(starting_balance).when(dao).selectBalanceForUpdate(anyString());
		doNothing().when(dao).insertTransaction(any(TransactionResource.class));
		doNothing().when(dao).upsertBalance(anyString(), anyLong());
		
		//--Execute--------
		TransferAndTransactResponse result = service.transferAndTransact(request);

		//--Verify--------
		verify(dao).checkMultiIdempotency(any(UUID.class), anyString());
		verify(dao).selectBalanceForUpdate(anyString());
		verify(dao, times(2)).insertTransaction(any(TransactionResource.class));
		verify(dao).upsertBalance(anyString(), anyLong());

		assertNotNull(result);
		assertEquals(TransferAndTransactResponse.SUCCESS, result.getStatus());
		assertNotNull(result.getTransactions());
		assertEquals(2, result.getTransactions().size());
		
		TransactionResource transfer = result.getTransactions().get(0);
		TransactionResource transact = result.getTransactions().get(1);

		assertEquals(request.getTransactionRequest().getAccountNumber(), transfer.getAccountNumber());
		assertNull(transfer.getDebitCardNumber());
		assertEquals(request.getRequestUuid(), transfer.getRequestUuid());
		assertEquals((starting_balance - transfer_amount), transfer.getRunningBalanceAmount());
		assertEquals((0 - transfer_amount), transfer.getTransactionAmount());
		assertEquals(request.getTransferReservation().getTransactionMetaDataJson(), transfer.getTransactionMetaDataJson());
		assertEquals(TransactionResource.NORMAL, transfer.getTransactionTypeCode());
		// - this requires integration - assertNotNull(transfer.getTransactionUuid());

		assertEquals(request.getTransactionRequest().getAccountNumber(), transact.getAccountNumber());
		assertEquals(request.getTransactionRequest().getDebitCardNumber(), transact.getDebitCardNumber());
		assertEquals(request.getRequestUuid(), transact.getRequestUuid());
		assertNull(transact.getReservationUuid());
		assertEquals((starting_balance - transfer_amount + transaction_amount), transact.getRunningBalanceAmount());
		assertEquals(transaction_amount, transact.getTransactionAmount());
		assertEquals(request.getTransactionRequest().getTransactionMetaDataJson(), transact.getTransactionMetaDataJson());
		assertEquals(TransactionResource.NORMAL, transact.getTransactionTypeCode());
		// - this requires integration - assertNotNull( transact.getTransactionUuid() );

	}
	
	@Test
	public void testCreateTransaction_idempotencyThrows() {
		//--Prepare--------
		TransferAndTransactRequest request = setup_request();
		
		//--Setup--------
		doThrow(new RuntimeException()).when(dao).checkMultiIdempotency( any(UUID.class), anyString() );
		
		//--Execute--------
		assertThrows( RuntimeException.class, ()->{ service.transferAndTransact(request); });

		//--Verify--------
		verify(dao).checkMultiIdempotency(any(UUID.class), anyString());

	}
	
	@Test
	public void testCreateTransaction_getBalanceThrows() {
		long transfer_amount =  -10000L;
		long transaction_amount = -15000L;
		//--Prepare--------
		TransferAndTransactRequest request = setup_request();
		request.getTransferReservation().setTransactionAmount(transfer_amount);
		request.getTransactionRequest().setTransactionAmount(transaction_amount);
		
		//--Setup--------
		when(dao.checkMultiIdempotency( any(UUID.class) , anyString())).thenReturn(Collections.emptyList());
		doThrow(new RuntimeException()).when(dao).selectBalanceForUpdate(anyString());
		
		//--Execute--------
		assertThrows( RuntimeException.class, ()->{ service.transferAndTransact(request); });

		//--Verify--------
		verify(dao).checkMultiIdempotency(any(UUID.class), anyString());
		verify(dao).selectBalanceForUpdate(anyString());

	}

	@Test
	public void testCreateTransaction_insertThrows() {
		long starting_balance = 10000L;
		long transfer_amount =  -10000L;
		long transaction_amount = -15000L;
		//--Prepare--------
		TransferAndTransactRequest request = setup_request();
		request.getTransferReservation().setTransactionAmount(transfer_amount);
		request.getTransactionRequest().setTransactionAmount(transaction_amount);
		
		//--Setup--------
		when(dao.checkMultiIdempotency( any(UUID.class), anyString() )).thenReturn(Collections.emptyList());
		doReturn(starting_balance).when(dao).selectBalanceForUpdate(anyString());
		doThrow(new RuntimeException()).when(dao).insertTransaction(any(TransactionResource.class));
		
		//--Execute--------
		assertThrows( RuntimeException.class, ()->{ service.transferAndTransact(request); });

		//--Verify--------
		verify(dao).checkMultiIdempotency(any(UUID.class), anyString());
		verify(dao).selectBalanceForUpdate(anyString());
		verify(dao, times(1)).insertTransaction(any(TransactionResource.class));

	}
	
	@Test
	public void testCreateTransaction_secondInsertThrows() {
		long starting_balance = 10000L;
		long transfer_amount =  -10000L;
		long transaction_amount = -15000L;
		//--Prepare--------
		TransferAndTransactRequest request = setup_request();
		request.getTransferReservation().setTransactionAmount(transfer_amount);
		request.getTransactionRequest().setTransactionAmount(transaction_amount);
		
		//--Setup--------
		when(dao.checkMultiIdempotency( any(UUID.class), anyString() )).thenReturn(Collections.emptyList());
		doReturn(starting_balance).when(dao).selectBalanceForUpdate(anyString());
		doNothing().doThrow(new RuntimeException()).when(dao).insertTransaction(any(TransactionResource.class));
		
		//--Execute--------
		assertThrows( RuntimeException.class, ()->{ service.transferAndTransact(request); });

		//--Verify--------
		verify(dao).checkMultiIdempotency(any(UUID.class), anyString());
		verify(dao).selectBalanceForUpdate(anyString());
		verify(dao, times(2)).insertTransaction(any(TransactionResource.class));

	}
	
	@Test
	public void testCreateTransaction_upsertThrows() {
		long starting_balance = 10000L;
		long transfer_amount =  -10000L;
		long transaction_amount = -15000L;
		//--Prepare--------
		TransferAndTransactRequest request = setup_request();
		request.getTransferReservation().setTransactionAmount(transfer_amount);
		request.getTransactionRequest().setTransactionAmount(transaction_amount);
		
		//--Setup--------
		when(dao.checkMultiIdempotency( any(UUID.class), anyString() )).thenReturn(Collections.emptyList());
		doReturn(starting_balance).when(dao).selectBalanceForUpdate(anyString());
		doNothing().when(dao).insertTransaction(any(TransactionResource.class));
		doThrow(new RuntimeException()).when(dao).upsertBalance(anyString(), anyLong());
		
		//--Execute--------
		assertThrows( RuntimeException.class, ()->{ service.transferAndTransact(request); });

		//--Verify--------
		verify(dao).checkMultiIdempotency(any(UUID.class), anyString());
		verify(dao).selectBalanceForUpdate(anyString());
		verify(dao, times(2)).insertTransaction(any(TransactionResource.class));
		verify(dao).upsertBalance(anyString(), anyLong());

	}
	
	private TransferAndTransactRequest setup_request() {
		TransactionResource transferReservation = new TransactionResource();
		transferReservation.setAccountNumber("2738479234");
		transferReservation.setDebitCardNumber(null);
		transferReservation.setRequestUuid(UUID.randomUUID());
		transferReservation.setTransactionUuid(UUID.randomUUID());
		transferReservation.setRunningBalanceAmount(778574L);
		transferReservation.setTransactionAmount(-7898L);
		transferReservation.setTransactionMetaDataJson("{}");
		transferReservation.setTransactionTypeCode(TransactionResource.RESERVATION);

		TransactionRequest transactionRequest = new TransactionRequest();
		transactionRequest.setRequestUuid(UUID.randomUUID());
		transactionRequest.setAccountNumber("1234567890234");
		transactionRequest.setDebitCardNumber("1235671234678234");
		transactionRequest.setTransactionAmount(-2323);
		transactionRequest.setTransactionMetaDataJson("{\"value\":23498234}");
		transactionRequest.setAuthorizeAgainstBalance(false);
		
		TransferAndTransactRequest request = new TransferAndTransactRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setTransactionRequest(transactionRequest);
		request.setTransferReservation(transferReservation);
		return request;
	}

}

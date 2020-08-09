package qslv.transaction.rest.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import qslv.transaction.request.TransactionRequest;
import qslv.transaction.request.TransferAndTransactRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.TransferAndTransactResponse;
import qslv.transaction.rest.ConfigProperties;
import qslv.transaction.rest.TransactionController;
import qslv.transaction.rest.TransactionService;
import qslv.common.TimedResponse;
import qslv.common.TraceableRequest;

@ExtendWith(MockitoExtension.class)
class UnitTransactionControllerTest_transferAndTransact {
	@Mock
	TransactionService service;
	public ConfigProperties props = new ConfigProperties();

	TransactionController controller = new TransactionController();

	@BeforeEach
	public void setup() {
		controller.setService(service);
		props.setAitid("234234");
		controller.setConfigProperties(props);
	}

	@Test
	void transferAndTransact_success() {
		//--Setup----------
		HashMap<String, String> headers = setup_header();
		TransferAndTransactRequest request = setup_request();

		//--Prepare---------
		TransferAndTransactResponse setupResponse = new TransferAndTransactResponse();
		doReturn(setupResponse).when(service).transferAndTransact(any(TransferAndTransactRequest.class));
		
		//--Execute---------------
		TimedResponse<TransferAndTransactResponse> response = controller.postTransferAndTransact(headers, request);
		
		//--Verify----------------
		verify(service).transferAndTransact(any(TransferAndTransactRequest.class));
		assertSame (setupResponse, response.getPayload());

	}

	@Test
	void transferAndTransact_invalid_version() {
		//--Setup----------
		HashMap<String, String> headers = setup_header();
		TransferAndTransactRequest request = setup_request();
		headers.replace(TraceableRequest.ACCEPT_VERSION, "XXX");
		
		//--Execute---------------
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferAndTransact(headers, request);
		});

		//--Verify----------------
		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertTrue(ex.getLocalizedMessage().contains("Invalid version"));
	}
	
	@Test
	void transferAndTransact_missing_ait() {
		//--Setup----------
		HashMap<String, String> headers = setup_header();
		TransferAndTransactRequest request = setup_request();
		headers.remove(TraceableRequest.AIT_ID);
		
		//--Execute---------------
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferAndTransact(headers, request);
		});

		//--Verify----------------
		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertTrue(ex.getLocalizedMessage().contains(TraceableRequest.AIT_ID));
	}
	
	@Test
	void transferAndTransact_missing_version() {
		//--Setup----------
		HashMap<String, String> headers = setup_header();
		TransferAndTransactRequest request = setup_request();
		headers.remove(TraceableRequest.ACCEPT_VERSION);
		
		//--Execute---------------
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferAndTransact(headers, request);
		});

		//--Verify----------------
		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertTrue(ex.getLocalizedMessage().contains(TraceableRequest.ACCEPT_VERSION));
	}
	
	@Test
	void transferAndTransact_missing_taxonomy() {
		//--Setup----------
		HashMap<String, String> headers = setup_header();
		TransferAndTransactRequest request = setup_request();
		headers.remove(TraceableRequest.BUSINESS_TAXONOMY_ID);
		
		//--Execute---------------
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferAndTransact(headers, request);
		});

		//--Verify----------------
		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertTrue(ex.getLocalizedMessage().contains(TraceableRequest.BUSINESS_TAXONOMY_ID));
	}
	
	@Test
	void transferAndTransact_missing_correlation() {
		//--Setup----------
		HashMap<String, String> headers = setup_header();
		TransferAndTransactRequest request = setup_request();
		headers.remove(TraceableRequest.CORRELATION_ID);
		
		//--Execute---------------
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferAndTransact(headers, request);
		});

		//--Verify----------------
		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertTrue(ex.getLocalizedMessage().contains(TraceableRequest.CORRELATION_ID));
	}
	
	@Test
	void transferAndTransact_missing_transaction() {
		//--Setup----------
		HashMap<String, String> headers = setup_header();
		TransferAndTransactRequest request = setup_request();
		request.setTransactionRequest(null);
		
		//--Execute---------------
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferAndTransact(headers, request);
		});

		//--Verify----------------
		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertTrue(ex.getLocalizedMessage().contains("Transaction Request"));
	}
	
	@Test
	void transferAndTransact_missing_transfer() {
		//--Setup----------
		HashMap<String, String> headers = setup_header();
		TransferAndTransactRequest request = setup_request();
		request.setTransferReservation(null);
		
		//--Execute---------------
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferAndTransact(headers, request);
		});

		//--Verify----------------
		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertTrue(ex.getLocalizedMessage().contains("Transfer Reservation"));
	}	
	@Test
	void transferAndTransact_missing_transferUuid() {
		//--Setup----------
		HashMap<String, String> headers = setup_header();
		TransferAndTransactRequest request = setup_request();
		request.getTransferReservation().setTransactionUuid(null);
		
		//--Execute---------------
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferAndTransact(headers, request);
		});

		//--Verify----------------
		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertTrue(ex.getLocalizedMessage().contains("Transfer Reservation UUID"));
	}	
	@Test
	void transferAndTransact_bad_transfer_amount() {
		//--Setup----------
		HashMap<String, String> headers = setup_header();
		TransferAndTransactRequest request = setup_request();
		request.getTransferReservation().setTransactionAmount(1L);
		
		//--Execute---------------
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferAndTransact(headers, request);
		});

		//--Verify----------------
		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertTrue(ex.getMessage().contains("Transfer Reservation must LT Zero"));
	}
	@Test
	void transferAndTransact_zero_transfer_amount() {
		//--Setup----------
		HashMap<String, String> headers = setup_header();
		TransferAndTransactRequest request = setup_request();
		request.getTransferReservation().setTransactionAmount(0L);
		
		//--Execute---------------
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferAndTransact(headers, request);
		});

		//--Verify----------------
		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertTrue(ex.getMessage().contains("Transfer Reservation must LT Zero"));
	}
	@Test
	void transferAndTransact_bad_transaction_amount() {
		//--Setup----------
		HashMap<String, String> headers = setup_header();
		TransferAndTransactRequest request = setup_request();
		request.getTransactionRequest().setTransactionAmount(1L);
		
		//--Execute---------------
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferAndTransact(headers, request);
		});

		//--Verify----------------
		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertTrue(ex.getLocalizedMessage().contains("Transaction must LT Zero"));
	}
	
	@Test
	void transferAndTransact_zero_transaction_amount() {
		//--Setup----------
		HashMap<String, String> headers = setup_header();
		TransferAndTransactRequest request = setup_request();
		request.getTransactionRequest().setTransactionAmount(0L);

		//--Execute---------------
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferAndTransact(headers, request);
		});

		//--Verify----------------
		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertTrue(ex.getLocalizedMessage().contains("Transaction must LT Zero"));
	}
	
	@Test
	void transferAndTransact_missing_json() {
		//--Setup----------
		HashMap<String, String> headers = setup_header();
		TransferAndTransactRequest request = setup_request();
		request.getTransactionRequest().setTransactionMetaDataJson(null);
		
		//--Execute---------------
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferAndTransact(headers, request);
		});

		//--Verify----------------
		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertTrue(ex.getLocalizedMessage().contains("Missing transactionMetaDataJson"));
	}
	
	@Test
	void transferAndTransact_missing_account() {
		//--Setup----------
		HashMap<String, String> headers = setup_header();
		TransferAndTransactRequest request = setup_request();
		request.getTransactionRequest().setAccountNumber(null);

		//--Execute---------------
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferAndTransact(headers, request);
		});

		//--Verify----------------
		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertTrue(ex.getLocalizedMessage().contains("Missing accountNumber"));
	}

	private HashMap<String, String> setup_header() {
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put(TraceableRequest.AIT_ID, "12345");
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "7483495");
		headers.put(TraceableRequest.CORRELATION_ID, "273849273498273498");
		headers.put(TraceableRequest.ACCEPT_VERSION, "1_0");
		return headers;
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
		transactionRequest.setTransactionAmount(-2323L);
		transactionRequest.setTransactionMetaDataJson("{\"value\":23498234}");
		transactionRequest.setAuthorizeAgainstBalance(false);
		
		TransferAndTransactRequest request = new TransferAndTransactRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setTransactionRequest(transactionRequest);
		request.setTransferReservation(transferReservation);
		return request;
	}
}

package qslv.transaction.rest.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

import qslv.transaction.request.TransactionSearchRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.TransactionSearchResponse;
import qslv.transaction.rest.ConfigProperties;
import qslv.transaction.rest.TransactionController;
import qslv.transaction.rest.TransactionService;
import qslv.common.TimedResponse;
import qslv.common.TraceableRequest;

@ExtendWith(MockitoExtension.class)
class UnitTransactionControllerTest_getTransaction {
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
	void testgetTransaction_success() {
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put(TraceableRequest.AIT_ID, "12345");
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "7483495");
		headers.put(TraceableRequest.CORRELATION_ID, "273849273498273498");
		headers.put(TraceableRequest.ACCEPT_VERSION, "1_0");

		TransactionSearchRequest request = new TransactionSearchRequest();
		request.setTransactionUuid(UUID.randomUUID());

		TransactionResource setupResource = new TransactionResource();
		setupResource.setTransactionUuid(UUID.randomUUID());
		TransactionSearchResponse searchResponse = new TransactionSearchResponse(setupResource);

		when(service.findTransaction(any(TransactionSearchRequest.class))).thenReturn(searchResponse);
		TimedResponse<TransactionSearchResponse> response = controller.getTransaction(headers, request);
		verify(service).findTransaction(any(TransactionSearchRequest.class));

		assertNotNull( response.getPayload());
		assertSame( response.getPayload(), searchResponse);

	}

	@Test
	void testgetTransaction_failure() {
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put(TraceableRequest.AIT_ID, "12345");
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "7483495");
		headers.put(TraceableRequest.CORRELATION_ID, "273849273498273498");
		headers.put(TraceableRequest.ACCEPT_VERSION, "1_0");

		TransactionSearchRequest request = new TransactionSearchRequest();
		request.setTransactionUuid(UUID.randomUUID());

		when(service.findTransaction(any(TransactionSearchRequest.class)))
				.thenThrow(new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "garbage"));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.getTransaction(headers, request);
		});
		assert (ex.getStatus() == HttpStatus.NOT_ACCEPTABLE);

	}
	
	@Test
	void testgetTransaction_invalidVersion() {
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put(TraceableRequest.AIT_ID, "12345");
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "7483495");
		headers.put(TraceableRequest.CORRELATION_ID, "273849273498273498");
		headers.put(TraceableRequest.ACCEPT_VERSION, "XXX");

		TransactionSearchRequest request = new TransactionSearchRequest();
		request.setTransactionUuid(UUID.randomUUID());

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.getTransaction(headers, request);
		});
		assert (ex.getStatus() == HttpStatus.BAD_REQUEST);

	}
	
	@Test
	void testgetTransaction_validateInput() {
		HashMap<String, String> headers = new HashMap<String, String>();
		TransactionSearchRequest request = new TransactionSearchRequest();
		TransactionSearchResponse setupResponse = new TransactionSearchResponse();

		// --- No headers, no data
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.getTransaction(headers, request);
		});
		assert (ex.getStatus() == HttpStatus.BAD_REQUEST);

		// --- add AIT_ID
		headers.put(TraceableRequest.AIT_ID, "12345");
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.getTransaction(headers, request);
		});
		assert (ex.getStatus() == HttpStatus.BAD_REQUEST);

		// --- add BUSINESS_TAXONOMY_ID
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "7483495");
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.getTransaction(headers, request);
		});
		assert (ex.getStatus() == HttpStatus.BAD_REQUEST);

		// --- add CORRELATION_ID
		headers.put(TraceableRequest.CORRELATION_ID, "273849273498273498");
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.getTransaction(headers, request);
		});
		assert (ex.getStatus() == HttpStatus.BAD_REQUEST);

		// --- add CORRELATION_ID
		headers.put(TraceableRequest.ACCEPT_VERSION, "1_0");
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.getTransaction(headers, request);
		});
		assert (ex.getStatus() == HttpStatus.BAD_REQUEST);

		// --- add REQUEST UUID and REservation UUID
		request.setTransactionUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.getTransaction(headers, request);
		});
		assert (ex.getStatus() == HttpStatus.BAD_REQUEST);
		
		// just Request UUID
		request.setReservationUuid(null);
		when(service.findTransaction(any(TransactionSearchRequest.class))).thenReturn(setupResponse);
		controller.getTransaction(headers, request);
		verify(service).findTransaction(any(TransactionSearchRequest.class));
		
		// just Reservation UUID
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionUuid(null);
		when(service.findTransaction(any(TransactionSearchRequest.class))).thenReturn(setupResponse);
		controller.getTransaction(headers, request);
		verify(service, times(2)).findTransaction(any(TransactionSearchRequest.class));
	}

}

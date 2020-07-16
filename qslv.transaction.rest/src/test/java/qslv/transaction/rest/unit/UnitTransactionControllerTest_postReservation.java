package qslv.transaction.rest.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.Instant;
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
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.ReservationResponse;
import qslv.transaction.rest.ConfigProperties;
import qslv.transaction.rest.TransactionController;
import qslv.transaction.rest.TransactionService;
import qslv.common.TimedResponse;
import qslv.common.TraceableRequest;

@ExtendWith(MockitoExtension.class)
class UnitTransactionControllerTest_postReservation {
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
	void testPostReservation_success() {
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put(TraceableRequest.AIT_ID, "12345");
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "7483495");
		headers.put(TraceableRequest.CORRELATION_ID, "273849273498273498");

		TransactionRequest request = new TransactionRequest();
		request.setAccountNumber("237489237492");
		request.setDebitCardNumber("8398345345");
		request.setRequestUuid(UUID.randomUUID());
		request.setTransactionAmount(-2323L);
		request.setTransactionMetaDataJson("{blahblah}");

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
		setupResource.setTransactionTypeCode(TransactionResource.RESERVATION);

		when(service.createReservation(any(TransactionRequest.class)))
				.thenReturn(new ReservationResponse(ReservationResponse.SUCCESS, setupResource));
		TimedResponse<ReservationResponse> response = controller.postReservation(headers, request);
		verify(service).createReservation(any(TransactionRequest.class));
		assertTrue (response.getPayload().getStatus() == ReservationResponse.SUCCESS);

		assertTrue (response.getPayload().getResource().getTransactionUuid().equals(setupResource.getTransactionUuid()));
		assertTrue (response.getPayload().getResource().getAccountNumber().equals(setupResource.getAccountNumber()));
		assertTrue (response.getPayload().getResource().getDebitCardNumber().equals(setupResource.getDebitCardNumber()));
		assertTrue (response.getPayload().getResource().getInsertTimestamp() == setupResource.getInsertTimestamp());
		assertTrue (response.getPayload().getResource().getReservationUuid().equals(setupResource.getReservationUuid()));
		assertTrue (response.getPayload().getResource().getRequestUuid().equals(setupResource.getRequestUuid()));
		assertTrue (response.getPayload().getResource().getRunningBalanceAmount() == setupResource.getRunningBalanceAmount());
		assertTrue (response.getPayload().getResource().getTransactionAmount() == setupResource.getTransactionAmount());
		assertTrue (response.getPayload().getResource().getTransactionMetaDataJson()
				.equals(setupResource.getTransactionMetaDataJson()));
		assertTrue (response.getPayload().getResource().getTransactionTypeCode() == setupResource.getTransactionTypeCode());

	}
	
	@Test
	void testPostReservation_failure() {
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put(TraceableRequest.AIT_ID, "12345");
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "7483495");
		headers.put(TraceableRequest.CORRELATION_ID, "273849273498273498");

		TransactionRequest request = new TransactionRequest();
		request.setAccountNumber("237489237492");
		request.setDebitCardNumber("8398345345");
		request.setRequestUuid(UUID.randomUUID());
		request.setTransactionAmount(-2323L);
		request.setTransactionMetaDataJson("{blahblah}");

		when(service.createReservation(any(TransactionRequest.class)))
			.thenThrow(new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "garbage"));
		
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postReservation(headers, request);
		} );
		assertTrue( ex.getStatus() == HttpStatus.NOT_ACCEPTABLE);
		
	}

	@Test
	void testPostReservation_validateInput() {
		HashMap<String, String> headers = new HashMap<String, String>();
		TransactionRequest request = new TransactionRequest();
		TransactionResource setupResource = new TransactionResource();
		TimedResponse<ReservationResponse> response = null;

		// --- No headers, no data
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postReservation(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);

		// --- add AIT_ID
		headers.put(TraceableRequest.AIT_ID, "12345");
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postReservation(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);

		// --- add BUSINESS_TAXONOMY_ID
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "7483495");
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postReservation(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);

		// --- add CORRELATION_ID
		headers.put(TraceableRequest.CORRELATION_ID, "273849273498273498");
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postReservation(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);

		// --- add REQUEST UUID
		request.setRequestUuid(UUID.randomUUID());
		request.setTransactionAmount(0L);
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postReservation(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);

		// --- add ACCOUNT ID
		request.setAccountNumber("237489237492");
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postReservation(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);

		// --- add JSON
		request.setTransactionMetaDataJson("{blahblah}");
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postReservation(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);

		// --- add AMOUNT
		request.setTransactionAmount(-2323);
		when(service.createReservation(any(TransactionRequest.class)))
				.thenReturn(new ReservationResponse(ReservationResponse.SUCCESS, setupResource));
		response = controller.postReservation(headers, request);
		verify(service).createReservation(any(TransactionRequest.class));
		assertTrue (response.getPayload().getStatus() == ReservationResponse.SUCCESS);
	}

}

package qslv.transaction.rest.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import qslv.transaction.request.TransactionRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.ReservationResponse;
import qslv.transaction.rest.JdbcDao;
import qslv.common.TimedResponse;
import qslv.common.TraceableRequest;

@SpringBootTest
@AutoConfigureMockMvc
class UnitMvcTransactionApplicationTest_postReservation {

	public static final MediaType APPLICATION_JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON.getType(),
			MediaType.APPLICATION_JSON.getSubtype(), Charset.forName("utf8"));
	public TypeReference<TimedResponse<ReservationResponse>> responseReference = 
			new TypeReference<TimedResponse<ReservationResponse>>() {};
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	JdbcDao jdbcDao;
	@Mock
	JdbcTemplate template;

	@BeforeEach
	void setup() {
		jdbcDao.setJdbcTemplate(template);
	}
	
	@Test
	void testPostTransaction_success() throws Exception {
		TransactionRequest request = new TransactionRequest();
		request.setAccountNumber("237489237492");
		request.setDebitCardNumber("8398345345");
		request.setRequestUuid(UUID.randomUUID());
		request.setTransactionAmount(-2323L);
		request.setTransactionMetaDataJson("{\"intvalue\":829342}");
		
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
		String requestJson = mapper.writer().withDefaultPrettyPrinter().writeValueAsString(request);

		UUID test_uuid = UUID.randomUUID();
		
		//Mock database idempotency
		when(template.query( eq(JdbcDao.idempotentQuery_sql), 
				ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class), anyString() ) )
		.thenReturn(new ArrayList<TransactionResource>());

		//Mock database select balance
		when(template.queryForObject( any(String.class) ,eq(Long.class), any())).thenReturn(99999L);

		//Mock database upsert balance
		when(template.update( any(String.class), any(String.class), any(Long.class) )).thenReturn(1);

		//Mock database insert transaction
		doAnswer(invocation -> {
			KeyHolder keyHolder = invocation.getArgument(1);
			keyHolder.getKeyList().add(Collections.singletonMap("transaction_uuid", test_uuid));
			return null;
		}).when(template).update( any(PreparedStatementCreator.class), any(KeyHolder.class) );
		
		// post transaction
		String stringResult = this.mockMvc.perform(post("/Reservation")
				.contentType(APPLICATION_JSON_UTF8)
				.content(requestJson)
				.header(TraceableRequest.AIT_ID, "")
				.header(TraceableRequest.BUSINESS_TAXONOMY_ID, "")
				.header(TraceableRequest.CORRELATION_ID, "")
				.header(TraceableRequest.ACCEPT_VERSION, "1_0") )
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();

		TimedResponse<ReservationResponse> response = mapper.readValue(stringResult, responseReference);
		
		assert(response.getPayload().getStatus() == ReservationResponse.SUCCESS);
		assertNotNull(response.getPayload());

		assert(response.getPayload().getResource().getAccountNumber().equals(request.getAccountNumber()));
		assert(response.getPayload().getResource().getDebitCardNumber().equals(request.getDebitCardNumber()));
		assert(response.getPayload().getResource().getRequestUuid().equals(request.getRequestUuid()));
		assert(response.getPayload().getResource().getTransactionAmount() == request.getTransactionAmount());
		assert(response.getPayload().getResource().getTransactionMetaDataJson().equals(request.getTransactionMetaDataJson()));
		assertNull(response.getPayload().getResource().getReservationUuid());
		assert(response.getPayload().getResource().getTransactionTypeCode().equals(TransactionResource.RESERVATION));
		assert(response.getPayload().getResource().getTransactionUuid().equals(test_uuid));
		assert(response.getPayload().getResource().getRunningBalanceAmount() == 99999L - 2323L );
		assert(response.getPayload().getResource().getTransactionAmount() == -2323L);
	}
	
	@Test
	void testPostTransaction_insufficientFunds() throws Exception {
		TransactionRequest request = new TransactionRequest();
		request.setAccountNumber("237489237492");
		request.setDebitCardNumber("8398345345");
		request.setRequestUuid(UUID.randomUUID());
		request.setTransactionAmount(-232323L);
		request.setTransactionMetaDataJson("{\"intvalue\":829342}");
		
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
		String requestJson = mapper.writer().withDefaultPrettyPrinter().writeValueAsString(request);

		UUID test_uuid = UUID.randomUUID();
		
		//Mock database idempotency
		when(template.query( eq(JdbcDao.idempotentQuery_sql), 
				ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class), anyString() ) )
		.thenReturn(new ArrayList<TransactionResource>());

		//Mock database select balance
		when(template.queryForObject( any(String.class) ,eq(Long.class), any())).thenReturn(99999L);

		//Mock database upsert balance
		when(template.update( any(String.class), any(String.class), any(Long.class) )).thenReturn(1);

		//Mock database insert transaction
		doAnswer(invocation -> {
			KeyHolder keyHolder = invocation.getArgument(1);
			keyHolder.getKeyList().add(Collections.singletonMap("transaction_uuid", test_uuid));
			return null;
		}).when(template).update( any(PreparedStatementCreator.class), any(KeyHolder.class) );
		
		// post transaction
		String stringResult = this.mockMvc.perform(post("/Reservation")
				.contentType(APPLICATION_JSON_UTF8)
				.content(requestJson)
				.header(TraceableRequest.AIT_ID, "")
				.header(TraceableRequest.BUSINESS_TAXONOMY_ID, "")
				.header(TraceableRequest.CORRELATION_ID, "")
				.header(TraceableRequest.ACCEPT_VERSION, "1_0") )
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();

		TimedResponse<ReservationResponse> response = mapper.readValue(stringResult, responseReference);
		
		assert(response.getPayload().getStatus() == ReservationResponse.INSUFFICIENT_FUNDS);
		assertNotNull(response.getPayload());

		assert(response.getPayload().getResource().getAccountNumber().equals(request.getAccountNumber()));
		assert(response.getPayload().getResource().getDebitCardNumber().equals(request.getDebitCardNumber()));
		assert(response.getPayload().getResource().getRequestUuid().equals(request.getRequestUuid()));
		assert(response.getPayload().getResource().getTransactionAmount() == request.getTransactionAmount());
		assert(response.getPayload().getResource().getTransactionMetaDataJson().equals(request.getTransactionMetaDataJson()));
		assertNull(response.getPayload().getResource().getReservationUuid());
		assert(response.getPayload().getResource().getTransactionTypeCode().equals(TransactionResource.REJECTED_TRANSACTION));
		assert(response.getPayload().getResource().getTransactionUuid().equals(test_uuid));
		assert(response.getPayload().getResource().getRunningBalanceAmount() == 99999L );
		assert(response.getPayload().getResource().getTransactionAmount() == -232323L);
	}
	@Test
	void testPostTransaction_alreadyPresent() throws Exception {
		TransactionRequest request = new TransactionRequest();
		request.setAccountNumber("237489237492");
		request.setDebitCardNumber("8398345345");
		request.setRequestUuid(UUID.randomUUID());
		request.setTransactionAmount(-2323L);
		request.setTransactionMetaDataJson("{\"intvalue\":829342}");
		
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
		String requestJson = mapper.writer().withDefaultPrettyPrinter().writeValueAsString(request);

		TransactionResource resource = new TransactionResource();
		resource.setAccountNumber("787923472934");
		resource.setDebitCardNumber("23898234");
		resource.setInsertTimestamp(new Timestamp(Instant.now().toEpochMilli()));
		resource.setRequestUuid(request.getRequestUuid());
		resource.setReservationUuid(UUID.randomUUID());
		resource.setRunningBalanceAmount(99999L);
		resource.setTransactionAmount(-238492L);
		resource.setTransactionUuid(UUID.randomUUID());
		resource.setTransactionMetaDataJson("{\"intvalue\":72834782");
		resource.setTransactionTypeCode(TransactionResource.RESERVATION);
		
		//Mock database idempotency
		when(template.query( eq(JdbcDao.idempotentQuery_sql), 
				ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class), anyString() ) )
			.thenReturn(Collections.singletonList(resource));
		
		// post transaction
		String stringResult = this.mockMvc.perform(post("/Reservation")
				.contentType(APPLICATION_JSON_UTF8)
				.content(requestJson)
				.header(TraceableRequest.AIT_ID, "")
				.header(TraceableRequest.BUSINESS_TAXONOMY_ID, "")
				.header(TraceableRequest.ACCEPT_VERSION, "1_0")
				.header(TraceableRequest.CORRELATION_ID, "") )
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();

		TimedResponse<ReservationResponse> response = mapper.readValue(stringResult, responseReference);
		
		assert(response.getPayload().getStatus() == ReservationResponse.SUCCESS);
		assertNotNull(response.getPayload());

		assert(response.getPayload().getResource().getAccountNumber().equals(resource.getAccountNumber()));
		assert(response.getPayload().getResource().getDebitCardNumber().equals(resource.getDebitCardNumber()));
		assert(response.getPayload().getResource().getRequestUuid().equals(resource.getRequestUuid()));
		assert(response.getPayload().getResource().getTransactionAmount() == resource.getTransactionAmount());
		assert(response.getPayload().getResource().getTransactionMetaDataJson().equals(resource.getTransactionMetaDataJson()));
		assert(response.getPayload().getResource().getReservationUuid().equals(resource.getReservationUuid()));
		assert(response.getPayload().getResource().getTransactionTypeCode().equals(resource.getTransactionTypeCode()));
		assert(response.getPayload().getResource().getTransactionUuid().equals(resource.getTransactionUuid()));
		assert(response.getPayload().getResource().getRunningBalanceAmount() == resource.getRunningBalanceAmount() );
		assert(response.getPayload().getResource().getTransactionAmount() == resource.getTransactionAmount() );
	}
}

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
import qslv.transaction.response.TransactionResponse;
import qslv.transaction.rest.JdbcDao;
import qslv.common.TimedResponse;
import qslv.common.TraceableRequest;

@SpringBootTest
@AutoConfigureMockMvc
class UnitMvcTransactionApplicationTest_postTransaction {

	public static final MediaType APPLICATION_JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON.getType(),
			MediaType.APPLICATION_JSON.getSubtype(), Charset.forName("utf8"));
	public TypeReference<TimedResponse<TransactionResponse>> responseReference = 
			new TypeReference<TimedResponse<TransactionResponse>>() {};
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
		String stringResult = this.mockMvc.perform(post("/Transaction")
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

		TimedResponse<TransactionResponse> response = mapper.readValue(stringResult, responseReference);
		
		assertTrue(response.getPayload().getStatus() == TransactionResponse.SUCCESS);
		assertTrue(response.getServiceTimeElapsed() > 0L);
		assertNotNull(response.getPayload());

		assertTrue(response.getPayload().getTransactions().get(0).getAccountNumber().equals(request.getAccountNumber()));
		assertTrue(response.getPayload().getTransactions().get(0).getDebitCardNumber().equals(request.getDebitCardNumber()));
		assertTrue(response.getPayload().getTransactions().get(0).getRequestUuid().equals(request.getRequestUuid()));
		assertTrue(response.getPayload().getTransactions().get(0).getTransactionAmount() == request.getTransactionAmount());
		assertTrue(response.getPayload().getTransactions().get(0).getTransactionMetaDataJson().equals(request.getTransactionMetaDataJson()));
		assertNull(response.getPayload().getTransactions().get(0).getReservationUuid());
		assertTrue(response.getPayload().getTransactions().get(0).getTransactionTypeCode().equals(TransactionResource.NORMAL));
		assertTrue(response.getPayload().getTransactions().get(0).getTransactionUuid().equals(test_uuid));
		assertTrue(response.getPayload().getTransactions().get(0).getRunningBalanceAmount() == 99999L - 2323L );
		assertTrue(response.getPayload().getTransactions().get(0).getTransactionAmount() == -2323L);
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
		String stringResult = this.mockMvc.perform(post("/Transaction")
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

		TimedResponse<TransactionResponse> response = mapper.readValue(stringResult, responseReference);
		
		assertTrue(response.getPayload().getStatus() == TransactionResponse.SUCCESS);
		assertNotNull(response.getPayload());

		assertTrue(response.getPayload().getTransactions().get(0).getAccountNumber().equals(resource.getAccountNumber()));
		assertTrue(response.getPayload().getTransactions().get(0).getDebitCardNumber().equals(resource.getDebitCardNumber()));
		assertTrue(response.getPayload().getTransactions().get(0).getRequestUuid().equals(resource.getRequestUuid()));
		assertTrue(response.getPayload().getTransactions().get(0).getTransactionAmount() == resource.getTransactionAmount());
		assertTrue(response.getPayload().getTransactions().get(0).getTransactionMetaDataJson().equals(resource.getTransactionMetaDataJson()));
		assertTrue(response.getPayload().getTransactions().get(0).getReservationUuid().equals(resource.getReservationUuid()));
		assertTrue(response.getPayload().getTransactions().get(0).getTransactionTypeCode().equals(resource.getTransactionTypeCode()));
		assertTrue(response.getPayload().getTransactions().get(0).getTransactionUuid().equals(resource.getTransactionUuid()));
		assertTrue(response.getPayload().getTransactions().get(0).getRunningBalanceAmount() == resource.getRunningBalanceAmount() );
		assertTrue(response.getPayload().getTransactions().get(0).getTransactionAmount() == resource.getTransactionAmount() );
	}
}

package qslv.transaction.rest.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
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
import org.springframework.http.HttpHeaders;
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
import qslv.transaction.request.TransferAndTransactRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.TransactionResponse;
import qslv.transaction.response.TransferAndTransactResponse;
import qslv.transaction.rest.JdbcDao;
import qslv.util.Random;
import qslv.common.TimedResponse;
import qslv.common.TraceableRequest;

@SpringBootTest
@AutoConfigureMockMvc
class UnitMvcTransactionApplicationTest_postTransferAndTransact {

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
		//--Setup---------
		long starting_balance = 10000L;
		long transfer_amount =  -10000L;
		long transaction_amount = -15000L;

		TransferAndTransactRequest request = setup_request();
		request.getTransferReservation().setTransactionAmount(transfer_amount);
		request.getTransactionRequest().setTransactionAmount(transaction_amount);
		
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
		String requestJson = mapper.writer().withDefaultPrettyPrinter().writeValueAsString(request);

		UUID transferUUID = UUID.randomUUID();
		UUID transactUUID = UUID.randomUUID();
		
		//--Prepare---------
		doReturn(Collections.emptyList())
			.when(template)
			.query( eq(JdbcDao.idempotentQuery_sql), 
					ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class), anyString() );
		doReturn(starting_balance)
			.when(template)
			.queryForObject( eq(JdbcDao.getBalance_sql) ,eq(Long.class), any());
		doAnswer(invocation -> {
			KeyHolder keyHolder = invocation.getArgument(1);
			keyHolder.getKeyList().add(Collections.singletonMap("transaction_uuid", transferUUID));
			return null;
		})
			.doAnswer(invocation -> {
				KeyHolder keyHolder = invocation.getArgument(1);
				keyHolder.getKeyList().add(Collections.singletonMap("transaction_uuid", transactUUID));
				return null;
			})
			.when(template)
			.update( any(PreparedStatementCreator.class), any(KeyHolder.class) );
		
		doReturn(1)
		.when(template)
		.update( eq(JdbcDao.upsert_balance_sql), any(String.class), any(Long.class) );
		
		//--Execute---------
		String stringResult = this.mockMvc.perform(post("/TransferAndTransact")
				.contentType(APPLICATION_JSON_UTF8)
				.content(requestJson)
				.headers( setup_header()) )
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();

		//--Verify---------
		TimedResponse<TransactionResponse> response = mapper.readValue(stringResult, responseReference);
		
		assertNotNull( response.getPayload());
		assertTrue(response.getServiceTimeElapsed() > 0L);

		assertEquals(TransferAndTransactResponse.SUCCESS, response.getPayload().getStatus());
		assertNotNull(response.getPayload().getTransactions());
		assertEquals(2, response.getPayload().getTransactions().size());
		
		TransactionResource transfer = response.getPayload().getTransactions().get(0);
		TransactionResource transact = response.getPayload().getTransactions().get(1);

		assertEquals(request.getTransactionRequest().getAccountNumber(), transfer.getAccountNumber());
		assertNull(transfer.getDebitCardNumber());
		assertEquals(request.getRequestUuid(), transfer.getRequestUuid());
		assertEquals((starting_balance - transfer_amount), transfer.getRunningBalanceAmount());
		assertEquals((0 - transfer_amount), transfer.getTransactionAmount());
		assertEquals(request.getTransferReservation().getTransactionMetaDataJson(), transfer.getTransactionMetaDataJson());
		assertEquals(TransactionResource.NORMAL, transfer.getTransactionTypeCode());
		assertEquals(transferUUID, transfer.getTransactionUuid());

		assertEquals(request.getTransactionRequest().getAccountNumber(), transact.getAccountNumber());
		assertEquals(request.getTransactionRequest().getDebitCardNumber(), transact.getDebitCardNumber());
		assertEquals(request.getRequestUuid(), transact.getRequestUuid());
		assertNull(transact.getReservationUuid());
		assertEquals((starting_balance - transfer_amount + transaction_amount), transact.getRunningBalanceAmount());
		assertEquals(transaction_amount, transact.getTransactionAmount());
		assertEquals(request.getTransactionRequest().getTransactionMetaDataJson(), transact.getTransactionMetaDataJson());
		assertEquals(TransactionResource.NORMAL, transact.getTransactionTypeCode());
		assertEquals(transactUUID, transact.getTransactionUuid());
	}

	
	@Test
	void testPostTransaction_alreadyPresent() throws Exception {
		//--Setup---------
		long transfer_amount =  -10000L;
		long transaction_amount = -15000L;

		TransferAndTransactRequest request = setup_request();
		request.getTransferReservation().setTransactionAmount(transfer_amount);
		request.getTransactionRequest().setTransactionAmount(transaction_amount);
		
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
		String requestJson = mapper.writer().withDefaultPrettyPrinter().writeValueAsString(request);
		
		ArrayList<TransactionResource> setup_list = new ArrayList<TransactionResource>();
		setup_list.add(randomTransaction());
		setup_list.add(randomTransaction());
		
		//--Prepare---------
		doReturn(setup_list)
			.when(template)
			.query( eq(JdbcDao.idempotentQuery_sql), 
					ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class), anyString() );
		
		//--Execute---------
		String stringResult = this.mockMvc.perform(post("/TransferAndTransact")
				.contentType(APPLICATION_JSON_UTF8)
				.content(requestJson)
				.headers( setup_header()) )
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();

		//--Verify---------
		TimedResponse<TransactionResponse> response = mapper.readValue(stringResult, responseReference);
		
		assertNotNull( response.getPayload());
		assertTrue(response.getServiceTimeElapsed() > 0L);

		assertEquals(TransferAndTransactResponse.SUCCESS, response.getPayload().getStatus());
		assertNotNull(response.getPayload().getTransactions());
		assertEquals(2, response.getPayload().getTransactions().size());
		
		TransactionResource transfer = response.getPayload().getTransactions().get(0);
		TransactionResource transact = response.getPayload().getTransactions().get(1);

		assertEquals(setup_list.get(0).getAccountNumber(), transfer.getAccountNumber());
		assertEquals(setup_list.get(0).getDebitCardNumber(), transfer.getDebitCardNumber());
		assertEquals(setup_list.get(0).getInsertTimestamp(), transfer.getInsertTimestamp());
		assertEquals(setup_list.get(0).getRequestUuid(), transfer.getRequestUuid());
		assertEquals(setup_list.get(0).getReservationUuid(), transfer.getReservationUuid());
		assertEquals(setup_list.get(0).getRunningBalanceAmount(), transfer.getRunningBalanceAmount());
		assertEquals(setup_list.get(0).getTransactionAmount(), transfer.getTransactionAmount());
		assertEquals(setup_list.get(0).getTransactionTypeCode(), transfer.getTransactionTypeCode());
		assertEquals(setup_list.get(0).getTransactionUuid(), transfer.getTransactionUuid());
		assertEquals(setup_list.get(0).getTransactionMetaDataJson(), transfer.getTransactionMetaDataJson());
		
		assertEquals(setup_list.get(1).getAccountNumber(), transact.getAccountNumber());
		assertEquals(setup_list.get(1).getDebitCardNumber(), transact.getDebitCardNumber());
		assertEquals(setup_list.get(1).getInsertTimestamp(), transact.getInsertTimestamp());
		assertEquals(setup_list.get(1).getRequestUuid(), transact.getRequestUuid());
		assertEquals(setup_list.get(1).getReservationUuid(), transact.getReservationUuid());
		assertEquals(setup_list.get(1).getRunningBalanceAmount(), transact.getRunningBalanceAmount());
		assertEquals(setup_list.get(1).getTransactionAmount(), transact.getTransactionAmount());
		assertEquals(setup_list.get(1).getTransactionTypeCode(), transact.getTransactionTypeCode());
		assertEquals(setup_list.get(1).getTransactionUuid(), transact.getTransactionUuid());
		assertEquals(setup_list.get(1).getTransactionMetaDataJson(), transact.getTransactionMetaDataJson());
	}
	
	private HttpHeaders setup_header() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(TraceableRequest.AIT_ID, "12345");
		headers.add(TraceableRequest.BUSINESS_TAXONOMY_ID, "7483495");
		headers.add(TraceableRequest.CORRELATION_ID, "273849273498273498");
		headers.add(TraceableRequest.ACCEPT_VERSION, "1_0");
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
		transactionRequest.setTransactionAmount(-2323);
		transactionRequest.setTransactionMetaDataJson("{\"value\":23498234}");
		transactionRequest.setAuthorizeAgainstBalance(false);
		
		TransferAndTransactRequest request = new TransferAndTransactRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setTransactionRequest(transactionRequest);
		request.setTransferReservation(transferReservation);
		return request;
	}

	private TransactionResource randomTransaction() {
		TransactionResource transaction = new TransactionResource();
		transaction.setAccountNumber(Random.randomDigits(12));
		transaction.setDebitCardNumber(Random.randomDigits(16));
		transaction.setInsertTimestamp(Timestamp.from(Instant.now()));
		transaction.setRequestUuid(UUID.randomUUID());
		transaction.setReservationUuid(UUID.randomUUID());
		transaction.setRunningBalanceAmount(Random.randomLong(8924924L));
		transaction.setTransactionAmount(Random.randomLong(723854L));
		transaction.setTransactionTypeCode(TransactionResource.NORMAL);
		transaction.setTransactionUuid(UUID.randomUUID());
		transaction.setTransactionMetaDataJson(Random.randomString(45));
		return transaction;
	}
}

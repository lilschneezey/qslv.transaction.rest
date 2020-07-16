BEGIN;
if ( 4500 > select running_balance_am from account_balance where account_id=? for update,
BEGIN
	INSERT INTO transaction (request_uuid, account_id, debitCard_id, transaction_am, transactionType_cd, runningBalance_am, transactionMetaData_json)
	VALUES ( '010e891e-1239-4295-8adf-70d7eb3c06a0', '123456789012', '1234567890123456', 4500, 'RJ', 0.0, '{}')
	RETURNING transaction_uuid;
END,
BEGIN
	INSERT INTO transaction (request_uuid, account_id, debitCard_id, transaction_am, transactionType_cd, runningBalance_am, transactionMetaData_json)
	VALUES ( '010e891e-1239-4295-8adf-70d7eb3c06a0', '123456789012', '1234567890123456', 4500, 'TX', 0.0, '{}')
	RETURNING transaction_uuid;
	upsert into account_balance (account_id, runningbalance_am) values ('1234',34);
END)

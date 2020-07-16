create table transaction(
	transaction_uuid UUID PRIMARY KEY not null DEFAULT gen_random_uuid(),
	request_uuid UUID not null,
	account_id STRING not null,
	debitCard_id STRING default null,
	transaction_am INT8 not null,
	transactionType_cd STRING not null,
	runningBalance_am INT8 not null default 0,
	reservation_uuid UUID default null,
	transactionMetaData_json JSONB default null,
	insert_tsz TIMESTAMPTZ not null default now()
);
CREATE INDEX ON transaction (request_uuid);
CREATE INDEX ON transaction (account_id);

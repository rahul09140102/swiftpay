-- Create separate databases for each service
CREATE DATABASE swiftpay_gateway;
CREATE DATABASE swiftpay_ledger;
CREATE DATABASE swiftpay_analytics;

-- Grant all privileges to the swiftpay user
GRANT ALL PRIVILEGES ON DATABASE swiftpay_gateway   TO swiftpay;
GRANT ALL PRIVILEGES ON DATABASE swiftpay_ledger    TO swiftpay;
GRANT ALL PRIVILEGES ON DATABASE swiftpay_analytics TO swiftpay;

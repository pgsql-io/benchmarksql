# ----
# R graph to show latency of all transaction types.
# ----

# ----
# Read the runInfo.csv file.
# ----
runInfo <- read.csv("data/runInfo.csv", head=TRUE)

# ----
# Determine the grouping interval in seconds based on the
# run duration.
# ----
xmin <- @SKIP@
if (xmin < 0) {
    xmin <- runInfo$rampupMins
}
xmax <- runInfo$runMins + runInfo$rampupMins
for (interval in c(1, 2, 5, 10, 20, 60, 120, 300, 600)) {
    if ((xmax * 60) / interval <= 1000) {
        break
    }
}
idiv <- interval * 1000.0
skip <- xmin * 60000
cutoff <- xmax * 60000

# ----
# Read the result.csv
# ----
rawData <- read.csv("data/result.csv", head=TRUE)

# ----
# If the result file is truncated (like when reading it while
# the benchmark is still running), we need to remove an
# incomplete last line.
# ----
if (is.na(rawData[nrow(rawData),]$error))
{
    rawData <- rawData[0:(nrow(rawData) - 1), ]
}

# ----
# Select just the measurement part of the data and filter
# by transaction type.
# ----
rawData <- rawData[rawData$endms >= skip & rawData$endms < cutoff, ]
noBGData <- rawData[rawData$ttype != 'DELIVERY_BG', ]
newOrder <- rawData[rawData$ttype == 'NEW_ORDER', ]
payment <- rawData[rawData$ttype == 'PAYMENT', ]
orderStatus <- rawData[rawData$ttype == 'ORDER_STATUS', ]
stockLevel <- rawData[rawData$ttype == 'STOCK_LEVEL', ]
delivery <- rawData[rawData$ttype == 'DELIVERY', ]
deliveryBG <- rawData[rawData$ttype == 'DELIVERY_BG', ]

# ----
# Aggregate the latency grouped by interval.
# ----
aggNewOrder <- setNames(aggregate(newOrder$latency, list(elapsed=trunc(newOrder$endms / idiv) * idiv), mean),
		   c('elapsed', 'latency'));
aggPayment <- setNames(aggregate(payment$latency, list(elapsed=trunc(payment$endms / idiv) * idiv), mean),
		   c('elapsed', 'latency'));
aggOrderStatus <- setNames(aggregate(orderStatus$latency, list(elapsed=trunc(orderStatus$endms / idiv) * idiv), mean),
		   c('elapsed', 'latency'));
aggStockLevel <- setNames(aggregate(stockLevel$latency, list(elapsed=trunc(stockLevel$endms / idiv) * idiv), mean),
		   c('elapsed', 'latency'));
aggDelivery <- setNames(aggregate(delivery$latency, list(elapsed=trunc(delivery$endms / idiv) * idiv), mean),
		   c('elapsed', 'latency'));

# ----
# Determine the ymax by increasing in sqrt(2) steps until 98%
# of ALL latencies fit into the graph. Then multiply with 1.2
# to give some headroom for the legend.
# ----
ymax_total <- quantile(noBGData$latency, probs = 0.98)

ymax <- 1
sqrt2 <- sqrt(2.0)
while (ymax < ymax_total) {
    ymax <- ymax * sqrt2
}
if (ymax < (ymax_total * 1.2)) {
    ymax <- ymax * 1.2
}



# ----
# Start the output image.
# ----
svg("latency.svg", width=@WIDTH@, height=@HEIGHT@, pointsize=@POINTSIZE@)
par(mar=c(4,4,4,4), xaxp=c(10,200,19))

# ----
# Plot the Delivery latency graph.
# ----
plot (
	aggDelivery$elapsed / 60000.0, aggDelivery$latency,
	type='l', col="blue3", lwd=2,
	axes=TRUE,
	xlab="Elapsed Minutes",
	ylab="Latency in Milliseconds",
	xlim=c(xmin, xmax),
	ylim=c(0, ymax)
)

# ----
# Plot the StockLevel latency graph.
# ----
par(new=T)
plot (
	aggStockLevel$elapsed / 60000.0, aggStockLevel$latency,
	type='l', col="gray70", lwd=2,
	axes=FALSE,
	xlab="",
	ylab="",
	xlim=c(xmin, xmax),
	ylim=c(0, ymax)
)

# ----
# Plot the OrderStatus latency graph.
# ----
par(new=T)
plot (
	aggOrderStatus$elapsed / 60000.0, aggOrderStatus$latency,
	type='l', col="green3", lwd=2,
	axes=FALSE,
	xlab="",
	ylab="",
	xlim=c(xmin, xmax),
	ylim=c(0, ymax)
)

# ----
# Plot the Payment latency graph.
# ----
par(new=T)
plot (
	aggPayment$elapsed / 60000.0, aggPayment$latency,
	type='l', col="magenta3", lwd=2,
	axes=FALSE,
	xlab="",
	ylab="",
	xlim=c(xmin, xmax),
	ylim=c(0, ymax)
)

# ----
# Plot the NewOrder latency graph.
# ----
par(new=T)
plot (
	aggNewOrder$elapsed / 60000.0, aggNewOrder$latency,
	type='l', col="red3", lwd=2,
	axes=FALSE,
	xlab="",
	ylab="",
	xlim=c(xmin, xmax),
	ylim=c(0, ymax)
)

# ----
# Add legend, title and other decorations.
# ----
legend ("topleft",
	c("NEW_ORDER", "PAYMENT", "ORDER_STATUS", "STOCK_LEVEL", "DELIVERY"),
	fill=c("red3", "magenta3", "green3", "gray70", "blue3"))
title (main=c(
    paste0("Run #", runInfo$runID, " of BenchmarkSQL v", runInfo$jTPCCVersion),
    "Transaction Latency"
    ))
grid()
box()

# ----
# Generate the transaction summary and write it to
# data/tx_summary.csv
# ----
tx_total <- NROW(noBGData)

tx_name <- c(
	'NEW_ORDER',
	'PAYMENT',
	'ORDER_STATUS',
	'STOCK_LEVEL',
	'DELIVERY',
	'DELIVERY_BG',
	'tpmC',
	'tpmTotal')
tx_count <- c(
	NROW(newOrder),
	NROW(payment),
	NROW(orderStatus),
	NROW(stockLevel),
	NROW(delivery),
	NROW(deliveryBG),
	sprintf("%.2f", NROW(newOrder) / runInfo$runMins),
	sprintf("%.2f", NROW(noBGData) / runInfo$runMins))
tx_percent <- c(
	sprintf("%.3f%%", NROW(newOrder) / tx_total * 100.0),
	sprintf("%.3f%%", NROW(payment) / tx_total * 100.0),
	sprintf("%.3f%%", NROW(orderStatus) / tx_total * 100.0),
	sprintf("%.3f%%", NROW(stockLevel) / tx_total * 100.0),
	sprintf("%.3f%%", NROW(delivery) / tx_total * 100.0),
	NA,
	sprintf("%.3f%%", NROW(newOrder) / runInfo$runMins /
			  runInfo$runWarehouses / 0.1286),
	NA)
tx_90th <- c(
	sprintf("%.3f", quantile(newOrder$latency, probs=0.90) / 1000.0),
	sprintf("%.3f", quantile(payment$latency, probs=0.90) / 1000.0),
	sprintf("%.3f", quantile(orderStatus$latency, probs=0.90) / 1000.0),
	sprintf("%.3f", quantile(stockLevel$latency, probs=0.90) / 1000.0),
	sprintf("%.3f", quantile(delivery$latency, probs=0.90) / 1000.0),
	sprintf("%.3f", quantile(deliveryBG$latency, probs=0.90) / 1000.0),
	NA, NA)
tx_95th <- c(
	sprintf("%.3f", quantile(newOrder$latency, probs=0.95) / 1000.0),
	sprintf("%.3f", quantile(payment$latency, probs=0.95) / 1000.0),
	sprintf("%.3f", quantile(orderStatus$latency, probs=0.95) / 1000.0),
	sprintf("%.3f", quantile(stockLevel$latency, probs=0.95) / 1000.0),
	sprintf("%.3f", quantile(delivery$latency, probs=0.95) / 1000.0),
	sprintf("%.3f", quantile(deliveryBG$latency, probs=0.95) / 1000.0),
	NA, NA)
tx_98th <- c(
	sprintf("%.3f", quantile(newOrder$latency, probs=0.98) / 1000.0),
	sprintf("%.3f", quantile(payment$latency, probs=0.98) / 1000.0),
	sprintf("%.3f", quantile(orderStatus$latency, probs=0.98) / 1000.0),
	sprintf("%.3f", quantile(stockLevel$latency, probs=0.98) / 1000.0),
	sprintf("%.3f", quantile(delivery$latency, probs=0.98) / 1000.0),
	sprintf("%.3f", quantile(deliveryBG$latency, probs=0.98) / 1000.0),
	NA, NA)
tx_avg <- c(
	sprintf("%.3f", mean(newOrder$latency) / 1000.0),
	sprintf("%.3f", mean(payment$latency) / 1000.0),
	sprintf("%.3f", mean(orderStatus$latency) / 1000.0),
	sprintf("%.3f", mean(stockLevel$latency) / 1000.0),
	sprintf("%.3f", mean(delivery$latency) / 1000.0),
	sprintf("%.3f", mean(deliveryBG$latency) / 1000.0),
	NA, NA)
tx_max <- c(
	sprintf("%.3f", max(newOrder$latency) / 1000.0),
	sprintf("%.3f", max(payment$latency) / 1000.0),
	sprintf("%.3f", max(orderStatus$latency) / 1000.0),
	sprintf("%.3f", max(stockLevel$latency) / 1000.0),
	sprintf("%.3f", max(delivery$latency) / 1000.0),
	sprintf("%.3f", max(deliveryBG$latency) / 1000.0),
	NA, NA)
tx_limit <- c("5.0", "5.0", "5.0", "20.0", "5.0", "80.0", NA, NA)
tx_rbk <- c(
	sprintf("%.3f%%", sum(newOrder$rbk) / NROW(newOrder) * 100.0),
	NA, NA, NA, NA, NA, NA, NA)
tx_error <- c(
	sum(newOrder$error),
	sum(payment$error),
	sum(orderStatus$error),
	sum(stockLevel$error),
	sum(delivery$error),
	sum(deliveryBG$error),
	NA, NA)
tx_dskipped <- c(
	NA, NA, NA, NA, NA,
	sum(deliveryBG$dskipped),
	NA, NA)
tx_info <- data.frame(
	tx_name,
	tx_count,
	tx_percent,
	tx_90th,
	tx_95th,
	tx_98th,
	tx_avg,
	tx_max,
	tx_limit,
	tx_rbk,
	tx_error,
	tx_dskipped)

write.csv(tx_info, file = "data/tx_summary.csv", quote = FALSE, na = "N/A",
	row.names = FALSE)


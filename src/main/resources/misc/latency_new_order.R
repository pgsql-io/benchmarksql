# ----
# R graph to show latency and delay of new_order
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
newOrder <- rawData[rawData$ttype == 'NEW_ORDER', ]

# ----
# Aggregate the latency and delay grouped by interval.
# ----
aggLatency <- setNames(aggregate(newOrder$latency, list(elapsed=trunc(newOrder$endms / idiv) * idiv), mean),
		   c('elapsed', 'latency'));
aggDelay <- setNames(aggregate(newOrder$delay, list(elapsed=trunc(newOrder$endms / idiv) * idiv), mean),
		   c('elapsed', 'delay'));

ymax_total <- max(aggLatency$latency)
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
svg("latency_new_order.svg", width=@WIDTH@, height=@HEIGHT@, pointsize=@POINTSIZE@)
par(mar=c(4,4,4,4), xaxp=c(10,200,19))

# ----
# Plot the NewOrder latency graph.
# ----
plot (
	aggLatency$elapsed / 60000.0, aggLatency$latency,
	type='l', col="red3", lwd=2,
	axes=TRUE,
	xlab="Elapsed Minutes",
	ylab="Latency/Delay in Milliseconds",
	xlim=c(xmin, xmax),
	ylim=c(0, ymax)
)

# ----
# Plot the NewOrder delay graph.
# ----
par(new=T)
plot (
	aggDelay$elapsed / 60000.0, aggDelay$delay,
	type='l', col="blue3", lwd=2,
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
	c("Latency", "Delay"),
	fill=c("red3", "blue3"))
title (main=c(
    paste0("Run #", runInfo$runID, " of BenchmarkSQL v", runInfo$jTPCCVersion),
    "NewOrder Latency and Delay"
    ))
grid()
box()


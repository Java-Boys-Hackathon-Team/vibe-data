WITH MonthlyFlightCounts AS (SELECT Origin, month(FlightDate) AS Month, COUNT(*) AS TotalFlights
                             FROM flights.public.flights
                             GROUP BY Origin, month(FlightDate)
                             ORDER BY Month DESC, TotalFlights DESC),
     TopAirportsByMonth AS (SELECT Month, Origin, TotalFlights, RANK() OVER (PARTITION BY Month ORDER BY TotalFlights DESC) AS AirportRank
                            FROM MonthlyFlightCounts),
     FilteredFlights AS (SELECT f.*,
                                CASE
                                    WHEN f.DepTimeBlk IN ('0600-0659', '0700-0759', '0800-0859', '1600-1659', '1700-1759', '1800-1859') THEN 'Peak'
                                    ELSE 'Off-Peak' END AS TimeOfDay
                         FROM flights.public.flights f
                                  JOIN TopAirportsByMonth t ON f.Origin = t.Origin AND month(f.FlightDate) = t.Month
                         WHERE f.Cancelled = false
                           AND f.Diverted = false
                           AND t.AirportRank <= 10)
SELECT ff.Month,
       Origin,
       TimeOfDay,
       COUNT(*)                                                                     AS TotalFlights,
       ROUND(AVG(TaxiOut), 2)                                                       AS AvgTaxiOut,
       ROUND(AVG(DepDelay), 2)                                                      AS AvgDEPDelay,
       ROUND(AVG(ArrDelay), 2)                                                      AS AvgARRDelay,
       ROUND(CORR(TaxiOut, DepDelay), 2)                                            AS TaxiOut_DepDelay_Correlation,
       ROUND(CORR(TaxiOut, ArrDelay), 2)                                            AS TaxiOut_ArrDelay_Correlation,
       SUM(CASE WHEN DepDel15 = 1 THEN 1 ELSE 0 END)                                AS DelayedFlights,
       ROUND((SUM(CASE WHEN DepDel15 = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*)), 2) AS PercentDelayed
FROM FilteredFlights ff
GROUP BY ff.Month, Origin, TimeOfDay
ORDER BY ff.Month DESC, Origin, TimeOfDay;
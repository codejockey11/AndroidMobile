package com.example.metars;

public class Temperature
{
    public Double fValue;
    public Double cValue;
    public Double kValue;
    public Double rValue;
    public Double vValue;

    public Temperature(String tp, Double t)
    {
        switch (tp)
        {
            case "C":
            {
                cValue = t;
                fValue = ConvertCtoF(cValue);
                kValue = ConvertCtoK(cValue);
                rValue = ConvertKtoR(kValue);

                break;
            }

            case "F":
            {
                fValue = t;
                cValue = ConvertFtoC(fValue);
                kValue = ConvertCtoK(cValue);
                rValue = ConvertKtoR(kValue);

                break;
            }

            case "K":
            {
                kValue = t;
                cValue = ConvertKtoC(kValue);
                fValue = ConvertCtoF(cValue);
                rValue = ConvertKtoR(kValue);

                break;
            }
        }
    }

    public Double ConvertCtoF(Double t)
    {
        return (t * (9 / 5)) + 32;
    }

    public Double ConvertCtoK(Double t)
    {
        return t + 273.15;
    }

    public Double ConvertFtoC(Double t)
    {
        return (t - 32) * (5 / 9);
    }

    public Double ConvertKtoR(Double t)
    {
        Double tc = ConvertKtoC(t);

        Double tf = ConvertCtoF(tc);

        return tf + 459.69;
    }

    public Double ConvertKtoC(Double t)
    {
        return t - 273.15;
    }
}

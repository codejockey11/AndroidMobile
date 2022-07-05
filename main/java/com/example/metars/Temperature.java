package com.example.metars;

public class Temperature {
    public double fValue;
    public double cValue;
    public double kValue;
    public double rValue;
    public double vValue;

    public Temperature(String tp, double t) {
        switch (tp) {
            case "C": {
                cValue = t;
                fValue = ConvertCtoF(cValue);
                kValue = ConvertCtoK(cValue);
                rValue = ConvertKtoR(kValue);

                break;
            }

            case "F": {
                fValue = t;
                cValue = ConvertFtoC(fValue);
                kValue = ConvertCtoK(cValue);
                rValue = ConvertKtoR(kValue);

                break;
            }

            case "K": {
                kValue = t;
                cValue = ConvertKtoC(kValue);
                fValue = ConvertCtoF(cValue);
                rValue = ConvertKtoR(kValue);

                break;
            }
        }
    }

    public double ConvertCtoF(double t) {
        return (t * (9.0 / 5.0)) + 32.0;
    }

    public double ConvertCtoK(double t) {
        return t + 273.15;
    }

    public double ConvertFtoC(double t) {
        return (t - 32.0) * (5.0 / 9.0);
    }

    public double ConvertKtoR(double t) {
        double tc = ConvertKtoC(t);

        double tf = ConvertCtoF(tc);

        return tf + 459.69;
    }

    public double ConvertKtoC(double t) {
        return t - 273.15;
    }
}

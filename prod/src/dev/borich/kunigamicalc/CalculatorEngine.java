package dev.borich.kunigamicalc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CalculatorEngine {
    private final StringBuilder exprBuffer = new StringBuilder();
    private String topHistoryLine = "";
    private String mainDisplayLine = "0";
    private boolean justEvaluated = false;

    public String getTopLine() {
        if (!justEvaluated) {
            String preview = computeLivePreview();
            if (!preview.isEmpty()) {
                return "≈ " + preview;
            }
        }
        return topHistoryLine;
    }

    public String getMainLine() {
        return mainDisplayLine;
    }

    public String getActiveNumberPlain() {
        String tok = getCurrentNumberToken();
        if (tok.startsWith("-")) tok = tok.substring(1);
        if (tok.endsWith(".")) tok = tok.substring(0, tok.length() - 1);
        return tok;
    }

    public void onButtonPress(String key) {
        switch (key) {
            case "C":
                exprBuffer.setLength(0);
                topHistoryLine = "";
                mainDisplayLine = "0";
                justEvaluated = false;
                return;

            case "⌫":
                if (justEvaluated) {
                    topHistoryLine = "";
                    justEvaluated = false;
                    if ("ERROR".equals(mainDisplayLine)) {
                        exprBuffer.setLength(0);
                        mainDisplayLine = "0";
                        return;
                    }
                }
                if (exprBuffer.length() > 0) {
                    exprBuffer.deleteCharAt(exprBuffer.length() - 1);
                }
                updateDisplayFromBuffer();
                return;

            case "±":
                if ("ERROR".equals(mainDisplayLine)) return;
                justEvaluated = false;
                toggleSignOfLastNumber();
                updateDisplayFromBuffer();
                return;

            case "%":
                if ("ERROR".equals(mainDisplayLine)) return;
                justEvaluated = false;
                applyPercentageToLastNumber();
                updateDisplayFromBuffer();
                return;

            case "=":
                evaluateExpression();
                return;

            case "+":
            case "-":
            case "×":
            case "÷":
                if (justEvaluated) {
                    justEvaluated = false;
                    topHistoryLine = "";
                }
                while (exprBuffer.length() > 0 && isOperator(exprBuffer.charAt(exprBuffer.length() - 1))) {
                    exprBuffer.deleteCharAt(exprBuffer.length() - 1);
                }
                if (exprBuffer.length() == 0) {
                    exprBuffer.append("0");
                }
                exprBuffer.append(key.charAt(0));
                updateDisplayFromBuffer();
                return;

            case ".":
                if (justEvaluated) {
                    exprBuffer.setLength(0);
                    topHistoryLine = "";
                    justEvaluated = false;
                }
                String lastNum = getCurrentNumberToken();
                if (!lastNum.contains(".")) {
                    if (lastNum.isEmpty() || "-".equals(lastNum)) {
                        exprBuffer.append("0.");
                    } else {
                        exprBuffer.append(".");
                    }
                }
                updateDisplayFromBuffer();
                return;

            default:
                // Digit 0..9
                if (justEvaluated) {
                    exprBuffer.setLength(0);
                    topHistoryLine = "";
                    justEvaluated = false;
                }
                String curNum = getCurrentNumberToken();
                String digitsOnly = curNum.replace(".", "").replace("-", "");
                if (digitsOnly.length() >= 12) {
                    return;
                }
                if ("0".equals(curNum)) {
                    exprBuffer.setCharAt(exprBuffer.length() - 1, key.charAt(0));
                } else if ("-0".equals(curNum)) {
                    exprBuffer.setCharAt(exprBuffer.length() - 1, key.charAt(0));
                } else {
                    exprBuffer.append(key);
                }
                updateDisplayFromBuffer();
                break;
        }
    }

    private boolean isOperator(char c) {
        return c == '+' || c == '-' || c == '×' || c == '÷';
    }

    private String getCurrentNumberToken() {
        int i = exprBuffer.length() - 1;
        while (i >= 0) {
            char c = exprBuffer.charAt(i);
            if (isOperator(c)) {
                if (c == '-' && (i == 0 || isOperator(exprBuffer.charAt(i - 1)))) {
                    return exprBuffer.substring(i);
                }
                return exprBuffer.substring(i + 1);
            }
            i--;
        }
        return exprBuffer.toString();
    }

    private void toggleSignOfLastNumber() {
        if (exprBuffer.length() == 0) return;
        int i = exprBuffer.length() - 1;
        while (i >= 0 && !isOperator(exprBuffer.charAt(i))) {
            i--;
        }
        if (i == exprBuffer.length() - 1) {
            // Ends with an operator; ignore sign toggle until a digit is typed
            return;
        }
        if (i < 0) {
            if (!"0".equals(exprBuffer.toString())) {
                exprBuffer.insert(0, '-');
            }
        } else if (exprBuffer.charAt(i) == '-' && (i == 0 || isOperator(exprBuffer.charAt(i - 1)))) {
            exprBuffer.deleteCharAt(i);
        } else {
            exprBuffer.insert(i + 1, '-');
        }
    }

    private void applyPercentageToLastNumber() {
        String numStr = getCurrentNumberToken();
        if (numStr.isEmpty() || "-".equals(numStr)) return;
        try {
            BigDecimal val = new BigDecimal(numStr)
                    .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                    .stripTrailingZeros();
            int start = exprBuffer.length() - numStr.length();
            exprBuffer.replace(start, exprBuffer.length(), val.toPlainString());
        } catch (Exception ignored) {
        }
    }

    private String computeLivePreview() {
        if (exprBuffer.length() < 3) return "";
        String raw = exprBuffer.toString();
        if (isOperator(raw.charAt(raw.length() - 1))) return "";
        boolean hasBinaryOp = false;
        for (int i = 1; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (isOperator(c) && !(c == '-' && isOperator(raw.charAt(i - 1)))) {
                hasBinaryOp = true;
                break;
            }
        }
        if (!hasBinaryOp) return "";
        try {
            BigDecimal res = compute(raw);
            String plain = formatResultPlain(res);
            return formatNumberToken(plain);
        } catch (Exception e) {
            return "";
        }
    }

    private String formatResultPlain(BigDecimal res) {
        res = res.stripTrailingZeros();
        String plain = res.toPlainString();
        if (plain.contains(".") && plain.length() > 14) {
            int dotIdx = plain.indexOf('.');
            int maxScale = Math.max(0, 13 - dotIdx);
            res = res.setScale(Math.min(6, maxScale), RoundingMode.HALF_UP).stripTrailingZeros();
            plain = res.toPlainString();
        }
        return plain;
    }

    private void evaluateExpression() {
        if (exprBuffer.length() == 0) return;
        String raw = exprBuffer.toString();
        while (raw.length() > 0 && isOperator(raw.charAt(raw.length() - 1))) {
            raw = raw.substring(0, raw.length() - 1);
        }
        if (raw.isEmpty()) return;

        try {
            BigDecimal res = compute(raw);
            topHistoryLine = formatExpressionDisplay(raw) + " =";
            String plain = formatResultPlain(res);
            exprBuffer.setLength(0);
            if (plain.length() <= 18) {
                exprBuffer.append(plain);
            }
            mainDisplayLine = formatNumberToken(plain);
            justEvaluated = true;
        } catch (ArithmeticException divZero) {
            topHistoryLine = formatExpressionDisplay(raw);
            mainDisplayLine = "ERROR";
            exprBuffer.setLength(0);
            justEvaluated = true;
        } catch (Exception e) {
            mainDisplayLine = "0";
        }
    }

    private void updateDisplayFromBuffer() {
        if (exprBuffer.length() == 0) {
            mainDisplayLine = "0";
            return;
        }
        mainDisplayLine = formatExpressionDisplay(exprBuffer.toString());
    }

    private String formatExpressionDisplay(String raw) {
        StringBuilder out = new StringBuilder();
        StringBuilder num = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean unaryMinus = (c == '-' && (i == 0 || isOperator(raw.charAt(i - 1))));
            if (!isOperator(c) || unaryMinus) {
                num.append(c);
            } else {
                if (num.length() > 0) {
                    out.append(formatNumberToken(num.toString()));
                    num.setLength(0);
                }
                out.append(' ').append(c).append(' ');
            }
        }
        if (num.length() > 0) {
            out.append(formatNumberToken(num.toString()));
        }
        return out.toString();
    }

    private String formatNumberToken(String token) {
        if (token.isEmpty() || "-".equals(token)) return token;
        boolean neg = token.startsWith("-");
        String clean = neg ? token.substring(1) : token;
        int dot = clean.indexOf('.');
        String intPart = (dot >= 0) ? clean.substring(0, dot) : clean;
        String decPart = (dot >= 0) ? clean.substring(dot) : "";
        try {
            if (!intPart.isEmpty()) {
                BigDecimal bd = new BigDecimal(intPart);
                DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
                DecimalFormat df = new DecimalFormat("#,###", sym);
                intPart = df.format(bd);
            }
        } catch (Exception ignored) {
        }
        return (neg ? "-" : "") + intPart + decPart;
    }

    private BigDecimal compute(String expr) {
        List<BigDecimal> nums = new ArrayList<>();
        List<Character> ops = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            boolean unary = (c == '-' && (i == 0 || isOperator(expr.charAt(i - 1))));
            if (!isOperator(c) || unary) {
                cur.append(c);
            } else {
                nums.add(new BigDecimal(cur.toString()));
                cur.setLength(0);
                ops.add(c);
            }
        }
        if (cur.length() > 0) {
            nums.add(new BigDecimal(cur.toString()));
        }
        if (nums.isEmpty()) return BigDecimal.ZERO;

        // First pass: × and ÷
        for (int i = 0; i < ops.size(); ) {
            char op = ops.get(i);
            if (op == '×' || op == '÷') {
                BigDecimal a = nums.get(i);
                BigDecimal b = nums.get(i + 1);
                BigDecimal r = (op == '×')
                        ? a.multiply(b)
                        : a.divide(b, 10, RoundingMode.HALF_UP);
                nums.set(i, r);
                nums.remove(i + 1);
                ops.remove(i);
            } else {
                i++;
            }
        }

        // Second pass: + and -
        BigDecimal total = nums.get(0);
        for (int i = 0; i < ops.size(); i++) {
            char op = ops.get(i);
            BigDecimal b = nums.get(i + 1);
            total = (op == '+') ? total.add(b) : total.subtract(b);
        }
        return total;
    }
}

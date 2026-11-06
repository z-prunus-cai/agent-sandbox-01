package deploy

/**
 * Inline styles of the task options form, kept in one place. The Active Choices sanitizer removes style blocks and most
 * classes, so each element carries its style attribute; tests/test_parameter_page.py checks the sanitizer keeps them all.
 */
class FormStyle {
    public static final Map<String, String> STYLES = [
            // one box per selected task
            group : 'border:1px solid #d1d5db;border-radius:6px;padding:14px 20px;margin:0 0 18px 0',
            title : 'margin-bottom:12px',
            // fixed layout, so max-width on an input is measured against its cell
            table : 'width:100%;table-layout:fixed',
            // the name column and the input column of each row
            label : 'width:220px;padding:8px 24px 8px 0;vertical-align:middle',
            value : 'padding:8px 0;vertical-align:middle',
            // text inputs, never wider than the box on a narrow window
            text  : 'width:520px;max-width:100%',
            number: 'width:160px',
            select: 'width:160px;height:34px',
            // the explanation, on its own line under the input
            hint  : 'margin-top:4px;font-size:12px;color:#6b7280',
    ]
}

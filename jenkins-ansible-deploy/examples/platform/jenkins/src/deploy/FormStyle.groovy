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
            // the name column and the input column of each row
            label : 'padding:8px 24px 8px 0;vertical-align:middle;white-space:nowrap',
            value : 'padding:8px 0;vertical-align:middle',
            text  : 'width:480px',
            number: 'width:160px',
            select: 'width:160px;height:34px',
            // the explanation after an input
            hint  : 'margin-left:12px;color:#4b5563',
    ]
}

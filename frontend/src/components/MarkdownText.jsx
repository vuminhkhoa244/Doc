import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

export default function MarkdownText({ children }) {
  return (
    <div className="markdown-body">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          a: ({ href, children: linkChildren }) => (
            <a href={href} target="_blank" rel="noreferrer">
              {linkChildren}
            </a>
          ),
          table: ({ children: tableChildren }) => (
            <div className="md-table-wrap">
              <table>{tableChildren}</table>
            </div>
          ),
        }}
      >
        {children}
      </ReactMarkdown>
    </div>
  );
}
